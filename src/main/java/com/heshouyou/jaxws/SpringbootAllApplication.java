package com.heshouyou.jaxws;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@SpringBootApplication
@Configuration
public class SpringbootAllApplication {

	@Autowired
	private WebServiceEndpoint webServiceEndpoint;

	@Bean
	public JaxWsServer jaxWsServer(){
		HashMap<String, Object> map = new HashMap<>();
		map.put("/WebServiceEndpoint",webServiceEndpoint);
		return new JaxWsServer("localhost", 20003, map);
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringbootAllApplication.class, args);
	}
}
