package com.xyz.webserver.ca;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan("com.xyz.webserver.util")
@PropertySource("classpath:application.yml")
public class CreditAgencyNodeConf {
}
