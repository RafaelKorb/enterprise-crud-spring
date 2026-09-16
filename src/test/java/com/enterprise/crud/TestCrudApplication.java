package com.enterprise.crud;

import org.springframework.boot.SpringApplication;

public class TestCrudApplication {

	public static void main(String[] args) {
		SpringApplication.from(CrudApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
