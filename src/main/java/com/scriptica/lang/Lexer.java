package com.scriptica.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lexer {
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("let", TokenType.LET);
        KEYWORDS.put("const", TokenType.CONST);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("in", TokenType.IN);
        KEYWORDS.put("switch", TokenType.SWITCH);
        KEYWORDS.put("case", TokenType.CASE);
        KEYWORDS.put("default", TokenType.DEFAULT);
        KEYWORDS.put("defer", TokenType.DEFER);
        KEYWORDS.put("struct", TokenType.STRUCT);
        KEYWORDS.put("class", TokenType.CLASS);
        KEYWORDS.put("enum", TokenType.ENUM);
        KEYWORDS.put("break", TokenType.BREAK);
        KEYWORDS.put("continue", TokenType.CONTINUE);
        KEYWORDS.put("try", TokenType.TRY);
        KEYWORDS.put("catch", TokenType.CATCH);
        KEYWORDS.put("import", TokenType.IMPORT);
        KEYWORDS.put("func", TokenType.FUNC);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("null", TokenType.NULL);
        KEYWORDS.put("wait", TokenType.WAIT);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;

    public Lexer(String source) {
        this.source = source == null ? "" : source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> add(TokenType.LEFT_PAREN);
            case ')' -> add(TokenType.RIGHT_PAREN);
            case '{' -> add(TokenType.LEFT_BRACE);
            case '}' -> add(TokenType.RIGHT_BRACE);
            case '[' -> add(TokenType.LEFT_BRACKET);
            case ']' -> add(TokenType.RIGHT_BRACKET);
            case ',' -> add(TokenType.COMMA);
            case ';' -> add(TokenType.SEMICOLON);
            case ':' -> add(TokenType.COLON);
            case '?' -> add(TokenType.QUESTION);
            case '+' -> {
                if (match('+')) add(TokenType.PLUS_PLUS);
                else add(match('=') ? TokenType.PLUS_EQUAL : TokenType.PLUS);
            }
            case '-' -> {
                if (match('-')) add(TokenType.MINUS_MINUS);
                else add(match('=') ? TokenType.MINUS_EQUAL : TokenType.MINUS);
            }
            case '*' -> add(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR);
            case '%' -> add(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
            case '!' -> add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
            case '=' -> add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
            case '<' -> add(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
            case '>' -> add(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
            case '.' -> {
                if (match('.')) add(TokenType.RANGE);
                else add(TokenType.DOT);
            }
            case '&' -> {
                if (match('&')) add(TokenType.AND_AND);
                else throw error("Unexpected '&' (did you mean &&?)");
            }
            case '|' -> {
                if (match('|')) add(TokenType.OR_OR);
                else throw error("Unexpected '|' (did you mean ||?)");
            }
            case '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) {
                    blockComment();
                } else if (match('=')) {
                    add(TokenType.SLASH_EQUAL);
                } else {
                    add(TokenType.SLASH);
                }
            }
            case ' ', '\r', '\t' -> {
                // ignore
            }
            case '\n' -> {
                line++;
                column = 1;
            }
            case '"' -> string('"');
            case '`' -> string('`');
            default -> {
                if (isDigit(c)) number();
                else if (isAlpha(c)) identifier();
                else throw error("Unexpected character: '" + c + "'");
            }
        }
    }

    private void blockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                return;
            }
            char c = advance();
            if (c == '\n') {
                line++;
                column = 1;
            }
        }
        throw error("Unterminated block comment");
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.get(text);
        if (type == null) type = TokenType.IDENTIFIER;
        add(type);
    }

    private void number() {
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        String text = source.substring(start, current);
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw error("Invalid number: " + text);
        }
        add(TokenType.NUMBER, value);
    }

    private void string(char quote) {
        StringBuilder sb = new StringBuilder();
        int stringLine = line;
        int stringCol = column;
        while (!isAtEnd() && peek() != quote) {
            char c = advance();
            if (c == '\n') {
                line++;
                column = 1;
                sb.append('\n');
                continue;
            }
            if (c == '\\' && quote != '`') {
                if (isAtEnd()) break;
                char esc = advance();
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(esc);
                }
                continue;
            }
            sb.append(c);
        }
        if (isAtEnd()) {
            throw new LexerException("Unterminated string", stringLine, stringCol);
        }
        advance();
        add(TokenType.STRING, sb.toString());
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        column++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        char c = source.charAt(current);
        current++;
        column++;
        return c;
    }

    private void add(TokenType type) {
        add(type, null);
    }

    private void add(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, Math.max(1, column - text.length())));
    }

    private RuntimeException error(String message) {
        return new LexerException(message, line, Math.max(1, column - 1));
    }

    public static final class LexerException extends RuntimeException {
        public final int line;
        public final int column;

        public LexerException(String message, int line, int column) {
            super(message + " at " + line + ":" + column);
            this.line = line;
            this.column = column;
        }
    }
}
