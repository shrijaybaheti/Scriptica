package com.scriptica.lang;

public final class Token {
	public final TokenType type;
	public final String lexeme;
	public final Object literal;
	public final int line;
	public final int column;

	public Token(TokenType type, String lexeme, Object literal, int line, int column) {
		this.type = type;
		this.lexeme = lexeme;
		this.literal = literal;
		this.line = line;
		this.column = column;
	}

	@Override
	public String toString() {
		return type + " '" + lexeme + "' (" + line + ":" + column + ")";
	}
}

