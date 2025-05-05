package edu.sjsu.cmpe272.simpleblog.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// do not change these base packages otherwise /ruok stops working!
@ComponentScan(basePackages = {"edu.sjsu.cmpe", "edu.sjsu.cmpe272.simpleblog.server"})
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
