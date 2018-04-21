package com.heshouyou.jaxws;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.server.*;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.server.EndpointFactory;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.transport.http.HttpAdapter;
import io.netty.channel.*;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;
import java.net.URL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Netty具体处理Handler
 * @Author heshouyou
 * @Date Create in 2018/4/1 13:49
 **/
@ChannelHandler.Sharable
public class JaxWsHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, HttpAdapter> endpointMappings = new HashMap<String, HttpAdapter>();


    public JaxWsHandler(Map<String,Object> mappings) {
        for (Map.Entry<String, Object> entry : mappings.entrySet())
            this.endpointMappings.put(entry.getKey(), createEndpointAdapter(entry.getValue()));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            HttpVersion protocolVersion = request.getProtocolVersion();

            JaxWsRequestUrl jaxWsRequestUrl = JaxWsRequestUrl.newInstance(ctx, request);
            String path = jaxWsRequestUrl.contextPath.isEmpty() ? "/" : jaxWsRequestUrl.contextPath;
            HttpAdapter httpAdapter = endpointMappings.get(path);
            if (httpAdapter == null) {
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(protocolVersion, HttpResponseStatus.NOT_FOUND);
                channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }

            boolean alive = HttpHeaders.isKeepAlive(request);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(protocolVersion, HttpResponseStatus.OK);
            WebServiceContextDelegate delegate = createDelegate(httpAdapter, jaxWsRequestUrl);
            JaxwsConnection connection = new JaxwsConnection(request, response, jaxWsRequestUrl, delegate);

            if (request.getMethod() == HttpMethod.GET && isWsdlRequest(jaxWsRequestUrl.queryString)) {
                httpAdapter.publishWSDL(connection);
            } else {
                httpAdapter.handle(connection);
            }

            if (alive) {
                request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                if (response.headers().get(HttpHeaders.Names.CONTENT_LENGTH) == null)
                    response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
            } else {
                response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
            }

            ChannelFuture future = channel.writeAndFlush(response);
            if (!alive)
                future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel channel = ctx.channel();
        if (channel.isActive()) {
            HttpResponseStatus status = (cause instanceof TooLongFrameException) ?
                    HttpResponseStatus.BAD_REQUEST : HttpResponseStatus.INTERNAL_SERVER_ERROR;
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,status);
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.content().writeBytes(cause.toString().getBytes());

            channel.write(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
    /**
     * 创建webservice端点适配器
     * **/
    private HttpAdapter createEndpointAdapter(Object implementor) {
        // Check for WSDL location.
        Class implType = implementor.getClass();
        EndpointFactory.verifyImplementorClass(implType);
        String wsdlLocation = EndpointFactory.getWsdlLocation(implType);

        SDDocumentSource primaryWsdl = null;
        if (wsdlLocation != null) {
            ClassLoader cl = implType.getClassLoader();
            URL wsdlUrl = cl.getResource(wsdlLocation);
            if (wsdlUrl == null)
                throw new ServerRtException("cannot.load.wsdl", wsdlLocation);

            primaryWsdl = SDDocumentSource.create(wsdlUrl);
        }

        WSEndpoint endpoint = WSEndpoint.create(implementor.getClass(), true,
                InstanceResolver.createSingleton(implementor).createInvoker(),
                null, null,null,
                BindingImpl.create(BindingID.parse(implementor.getClass())),
                primaryWsdl,
                null, null, true);

        return HttpAdapter.createAlone(endpoint);
    }

    /**
     * Create a new {@link WebServiceContextDelegate}.
     *
     * @param adapter HTTP adapter.
     * @param jaxWsRequestUrl JAX-WS URL for the request.
     * @return A new {@link WebServiceContextDelegate}.
     */
    private WebServiceContextDelegate createDelegate(final HttpAdapter adapter, final JaxWsRequestUrl jaxWsRequestUrl) {
        return new WebServiceContextDelegate() {
            @Override
            public Principal getUserPrincipal(@NotNull Packet request) {
                return null;
            }

            @Override
            public boolean isUserInRole(@NotNull Packet request, String role) {
                return false;
            }

            @Override
            public @NotNull String getEPRAddress(@NotNull Packet request, @NotNull WSEndpoint endpoint) {
                PortAddressResolver resolver = adapter.owner.createPortAddressResolver(jaxWsRequestUrl.baseAddress, endpoint.getClass());
                QName portName = endpoint.getPortName();
                String address = resolver.getAddressFor(endpoint.getServiceName(), portName.getLocalPart());
                if (address == null)
                    throw new WebServiceException("WsservletMessages.SERVLET_NO_ADDRESS_AVAILABLE(" + portName + ")");

                return address;
            }

            @Override
            public String getWSDLAddress(@NotNull Packet request, @NotNull WSEndpoint endpoint) {
                return getEPRAddress(request, endpoint) + "?wsdl";
            }
        };
    }

    /**
     * 判断是否是一个webservice请求
     *
     * @param queryString The query string to check.
     * @return {@code true} if the specified query string represents a WSDL request.
     */
    private boolean isWsdlRequest(String queryString) {
        return queryString != null && (queryString.equalsIgnoreCase("wsdl") || queryString.startsWith("xsd="));
    }
}
