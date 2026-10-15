package com.example.demo.finance.statement;

/** Bir bankanın ekstre biçimini okur. Yeni bir banka için bu arayüzün yeni bir uygulaması yazılır. */
public interface StatementParser {

	/** Metnin bu bankaya ait olup olmadığı. */
	boolean supports(String text);

	ParsedStatement parse(String text);
}
