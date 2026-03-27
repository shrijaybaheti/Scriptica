package com.scriptica.lang;

import com.scriptica.lang.Ast.Expr;
import com.scriptica.lang.Ast.Stmt;

import java.util.ArrayList;
import java.util.List;

public final class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    public Expr parseExpression() {
        Expr expr = expression();
        if (!isAtEnd()) throw error(peek(), "Expected end of expression");
        return expr;
    }

    private Stmt declaration() {
        try {
            if (match(TokenType.STRUCT)) return structDeclaration(previous(), false);
            if (match(TokenType.CLASS)) return structDeclaration(previous(), true);
            if (match(TokenType.ENUM)) return enumDeclaration(previous());
            if (match(TokenType.LET)) return varDeclaration(false);
            if (match(TokenType.CONST)) return varDeclaration(true);
            if (match(TokenType.FUNC)) return functionDeclaration();
            return statement();
        } catch (ParseException e) {
            synchronize();
            throw e;
        }
    }

    private Stmt varDeclaration(boolean isConst) {
        // destructuring: let {a, b} = expr;
        if (match(TokenType.LEFT_BRACE)) {
            List<Token> names = new ArrayList<>();
            if (!check(TokenType.RIGHT_BRACE)) {
                do {
                    names.add(consume(TokenType.IDENTIFIER, "Expected name in destructuring"));
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_BRACE, "Expected '}' after destructuring");
            consume(TokenType.EQUAL, "Expected '=' after destructuring");
            Expr src = expression();
            optionalSemicolon();
            return new Stmt.Destructure(isConst, names, src);
        }

        Token name = consume(TokenType.IDENTIFIER, "Expected variable name");
        Expr init = null;
        if (match(TokenType.EQUAL)) {
            init = expression();
        } else if (isConst) {
            throw error(peek(), "const requires an initializer");
        }
        optionalSemicolon();
        return isConst ? new Stmt.Const(name, init) : new Stmt.Let(name, init);
    }

    private Stmt structDeclaration(Token keyword, boolean isClass) {
        Token name = consume(TokenType.IDENTIFIER, "Expected name after " + keyword.lexeme);
        consume(TokenType.LEFT_BRACE, "Expected '{' after " + name.lexeme);

        List<Token> fields = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            fields.add(consume(TokenType.IDENTIFIER, "Expected field name"));
            match(TokenType.COMMA);
            match(TokenType.SEMICOLON);
        }

        consume(TokenType.RIGHT_BRACE, "Expected '}' after " + name.lexeme);
        optionalSemicolon();
        return new Stmt.Struct(keyword, name, isClass, fields);
    }

    private Stmt enumDeclaration(Token keyword) {
        Token name = consume(TokenType.IDENTIFIER, "Expected enum name");
        consume(TokenType.LEFT_BRACE, "Expected '{' after enum name");

        List<Stmt.EnumMember> members = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            Token mName = consume(TokenType.IDENTIFIER, "Expected enum member name");
            Expr value = null;
            if (match(TokenType.EQUAL)) {
                value = expression();
            }
            members.add(new Stmt.EnumMember(mName, value));
            if (!match(TokenType.COMMA)) {
                match(TokenType.SEMICOLON);
            }
        }

        consume(TokenType.RIGHT_BRACE, "Expected '}' after enum");
        optionalSemicolon();
        return new Stmt.Enum(keyword, name, members);
    }

    private Stmt functionDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expected function name after func");
        consume(TokenType.LEFT_PAREN, "Expected '(' after function name");
        List<Stmt.Param> params = new ArrayList<>();
        boolean sawDefault = false;
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (params.size() >= 32) throw error(peek(), "Too many parameters (max 32)");
                Token pName = consume(TokenType.IDENTIFIER, "Expected parameter name");
                Expr def = null;
                if (match(TokenType.EQUAL)) {
                    sawDefault = true;
                    def = expression();
                } else if (sawDefault) {
                    throw error(peek(), "Non-default parameter cannot follow a default parameter");
                }
                params.add(new Stmt.Param(pName, def));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expected ')' after parameters");
        Stmt.Block body = blockStatement();
        return new Stmt.Function(name, params, body);
    }

    private Stmt statement() {
        if (match(TokenType.IMPORT)) return importStatement(previous());
        if (match(TokenType.SWITCH)) return switchStatement(previous());
        if (match(TokenType.DEFER)) return deferStatement(previous());
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.FOR)) return forStatement(previous());
        if (match(TokenType.TRY)) return tryCatchStatement(previous());
        if (match(TokenType.BREAK)) return breakStatement(previous());
        if (match(TokenType.CONTINUE)) return continueStatement(previous());
        if (match(TokenType.RETURN)) return returnStatement();
        if (match(TokenType.WAIT)) return waitStatement(previous());
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    private Stmt importStatement(Token keyword) {
        Expr spec = expression();
        optionalSemicolon();
        return new Stmt.Import(keyword, spec);
    }

    private Stmt deferStatement(Token keyword) {
        Stmt.Block block = blockStatement();
        return new Stmt.Defer(keyword, block);
    }

    private Stmt switchStatement(Token keyword) {
        consume(TokenType.LEFT_PAREN, "Expected '(' after switch");
        Expr value = expression();
        consume(TokenType.RIGHT_PAREN, "Expected ')' after switch expression");
        consume(TokenType.LEFT_BRACE, "Expected '{' after switch");

        List<Stmt.SwitchCase> cases = new ArrayList<>();

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            if (match(TokenType.CASE)) {
                Expr matchExpr = expression();
                consume(TokenType.COLON, "Expected ':' after case expression");
                List<Stmt> body = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
                    body.add(declaration());
                }
                cases.add(new Stmt.SwitchCase(matchExpr, false, body));
                continue;
            }
            if (match(TokenType.DEFAULT)) {
                consume(TokenType.COLON, "Expected ':' after default");
                List<Stmt> body = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
                    body.add(declaration());
                }
                cases.add(new Stmt.SwitchCase(new Expr.Literal(null), true, body));
                continue;
            }
            throw error(peek(), "Expected 'case' or 'default' in switch");
        }

        consume(TokenType.RIGHT_BRACE, "Expected '}' after switch");
        return new Stmt.Switch(keyword, value, cases);
    }

    private Stmt breakStatement(Token keyword) {
        optionalSemicolon();
        return new Stmt.Break(keyword);
    }

    private Stmt continueStatement(Token keyword) {
        optionalSemicolon();
        return new Stmt.Continue(keyword);
    }

    private Stmt tryCatchStatement(Token keyword) {
        Stmt.Block tryBlock = blockStatement();
        consume(TokenType.CATCH, "Expected 'catch' after try block");
        consume(TokenType.LEFT_PAREN, "Expected '(' after catch");
        Token errName = consume(TokenType.IDENTIFIER, "Expected error variable name");
        consume(TokenType.RIGHT_PAREN, "Expected ')' after catch variable");
        Stmt.Block catchBlock = blockStatement();
        return new Stmt.TryCatch(tryBlock, errName, catchBlock);
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        Stmt thenBranch = statementOrBlock("Expected statement after if condition");
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = statementOrBlock("Expected statement after else");
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        Expr condition = expression();
        Stmt body = statementOrBlock("Expected statement after while condition");
        return new Stmt.While(condition, body);
    }

    private Stmt statementOrBlock(String error) {
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
        return statement();
    }

    private Stmt forStatement(Token keyword) {
        consume(TokenType.LEFT_PAREN, "Expected '(' after for");

        // for (x in expr)
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.IN)) {
            Token name = consume(TokenType.IDENTIFIER, "Expected loop variable");
            consume(TokenType.IN, "Expected 'in'");
            Expr iterable = expression();
            consume(TokenType.RIGHT_PAREN, "Expected ')' after for-in");
            Stmt body = statementOrBlock("Expected statement after for");
            return new Stmt.ForEach(name, iterable, body);
        }

        // C-style
        Stmt initializer = null;
        if (match(TokenType.SEMICOLON)) {
            initializer = null;
        } else if (match(TokenType.LET)) {
            initializer = forVarDeclaration(false);
        } else if (match(TokenType.CONST)) {
            initializer = forVarDeclaration(true);
        } else {
            Expr initExpr = expression();
            consume(TokenType.SEMICOLON, "Expected ';' after for initializer");
            initializer = new Stmt.Expression(initExpr);
        }

        Expr condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = expression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after for condition");

        Expr increment = null;
        if (!check(TokenType.RIGHT_PAREN)) {
            increment = expression();
        }
        consume(TokenType.RIGHT_PAREN, "Expected ')' after for clauses");

        Stmt body = statementOrBlock("Expected statement after for");

        if (increment != null) {
            body = new Stmt.Block(List.of(body, new Stmt.Expression(increment)));
        }
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);
        if (initializer != null) {
            body = new Stmt.Block(List.of(initializer, body));
        }
        return body;
    }

    private Stmt forVarDeclaration(boolean isConst) {
        Token name = consume(TokenType.IDENTIFIER, "Expected variable name");
        Expr init = null;
        if (match(TokenType.EQUAL)) {
            init = expression();
        } else if (isConst) {
            throw error(peek(), "const requires an initializer");
        }
        consume(TokenType.SEMICOLON, "Expected ';' after for variable");
        return isConst ? new Stmt.Const(name, init) : new Stmt.Let(name, init);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(TokenType.SEMICOLON) && !check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            value = expression();
        }
        optionalSemicolon();
        return new Stmt.Return(keyword, value);
    }

    private Stmt waitStatement(Token keyword) {
        Expr ticks = expression();
        optionalSemicolon();
        return new Stmt.Wait(keyword, ticks);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        optionalSemicolon();
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(TokenType.RIGHT_BRACE, "Expected '}' after block");
        return statements;
    }

    private Stmt.Block blockStatement() {
        consume(TokenType.LEFT_BRACE, "Expected '{' to start block");
        return new Stmt.Block(block());
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = conditional();

        if (match(TokenType.EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL, TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL, TokenType.PERCENT_EQUAL)) {
            Token op = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable v) {
                if (op.type == TokenType.EQUAL) {
                    return new Expr.Assign(v.name(), value);
                }
                Expr rhs = new Expr.Binary(expr, baseOperator(op), value);
                return new Expr.Assign(v.name(), rhs);
            }

            if (expr instanceof Expr.Get g) {
                if (op.type == TokenType.EQUAL) {
                    return new Expr.Set(g.target(), g.name(), value);
                }
                Expr get = new Expr.Get(g.target(), g.name());
                Expr rhs = new Expr.Binary(get, baseOperator(op), value);
                return new Expr.Set(g.target(), g.name(), rhs);
            }

            if (expr instanceof Expr.Index idx) {
                if (op.type == TokenType.EQUAL) {
                    return new Expr.IndexAssign(idx.target(), idx.bracket(), idx.index(), value);
                }
                Expr get = new Expr.Index(idx.target(), idx.bracket(), idx.index());
                Expr rhs = new Expr.Binary(get, baseOperator(op), value);
                return new Expr.IndexAssign(idx.target(), idx.bracket(), idx.index(), rhs);
            }

            throw error(op, "Invalid assignment target");
        }

        return expr;
    }

    private Token baseOperator(Token compound) {
        TokenType t = switch (compound.type) {
            case PLUS_EQUAL -> TokenType.PLUS;
            case MINUS_EQUAL -> TokenType.MINUS;
            case STAR_EQUAL -> TokenType.STAR;
            case SLASH_EQUAL -> TokenType.SLASH;
            case PERCENT_EQUAL -> TokenType.PERCENT;
            default -> TokenType.EQUAL;
        };
        String lex = switch (t) {
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case PERCENT -> "%";
            default -> compound.lexeme;
        };
        return new Token(t, lex, null, compound.line, compound.column);
    }

    private Expr incDec(Token op, Expr target, boolean prefix) {
        TokenType base = (op.type == TokenType.PLUS_PLUS) ? TokenType.PLUS : TokenType.MINUS;
        Token baseTok = new Token(base, base == TokenType.PLUS ? "+" : "-", null, op.line, op.column);
        Expr one = new Expr.Literal(1.0);

        if (target instanceof Expr.Variable v) {
            Expr get = new Expr.Variable(v.name());
            Expr rhs = new Expr.Binary(get, baseTok, one);
            return new Expr.Assign(v.name(), rhs);
        }
        if (target instanceof Expr.Get g) {
            Expr get = new Expr.Get(g.target(), g.name());
            Expr rhs = new Expr.Binary(get, baseTok, one);
            return new Expr.Set(g.target(), g.name(), rhs);
        }
        if (target instanceof Expr.Index idx) {
            Expr get = new Expr.Index(idx.target(), idx.bracket(), idx.index());
            Expr rhs = new Expr.Binary(get, baseTok, one);
            return new Expr.IndexAssign(idx.target(), idx.bracket(), idx.index(), rhs);
        }

        throw error(op, "Invalid ++/-- target");
    }

    private Expr conditional() {
        Expr expr = or();
        if (match(TokenType.QUESTION)) {
            Expr thenExpr = expression();
            consume(TokenType.COLON, "Expected ':' in ternary");
            Expr elseExpr = conditional();
            return new Expr.Ternary(expr, thenExpr, elseExpr);
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();
        while (match(TokenType.OR_OR)) {
            Token op = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, op, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (match(TokenType.AND_AND)) {
            Token op = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, op, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token op = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = range();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token op = previous();
            Expr right = range();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr range() {
        Expr expr = term();
        while (match(TokenType.RANGE)) {
            Token op = previous();
            Expr right = term();
            expr = new Expr.Range(expr, op, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS, TokenType.PLUS_PLUS, TokenType.MINUS_MINUS)) {
            Token op = previous();
            Expr right = unary();
            if (op.type == TokenType.PLUS_PLUS || op.type == TokenType.MINUS_MINUS) {
                return incDec(op, right, true);
            }
            return new Expr.Unary(op, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(TokenType.LEFT_BRACKET)) {
                Token bracket = previous();
                Expr idx = expression();
                consume(TokenType.RIGHT_BRACKET, "Expected ']' after index");
                expr = new Expr.Index(expr, bracket, idx);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER, "Expected property name after '.'");
                expr = new Expr.Get(expr, name);
            } else if (match(TokenType.PLUS_PLUS, TokenType.MINUS_MINUS)) {
                Token op = previous();
                expr = incDec(op, expr, false);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> args = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (args.size() >= 64) throw error(peek(), "Too many arguments (max 64)");
                args.add(expression());
            } while (match(TokenType.COMMA));
        }
        Token paren = consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments");
        return new Expr.Call(callee, paren, args);
    }

    private Expr primary() {
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);
        if (match(TokenType.NULL)) return new Expr.Literal(null);
        if (match(TokenType.NUMBER)) return new Expr.Literal(previous().literal);
        if (match(TokenType.STRING)) {
            Token t = previous();
            if (t.lexeme != null && t.lexeme.startsWith("`")) return new Expr.Template(t, (String) t.literal);
            return new Expr.Literal(t.literal);
        }
        if (match(TokenType.IDENTIFIER)) return new Expr.Variable(previous());

        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expected ')' after expression");
            return new Expr.Grouping(expr);
        }

        if (match(TokenType.LEFT_BRACKET)) {
            Token bracket = previous();
            List<Expr> els = new ArrayList<>();
            if (!check(TokenType.RIGHT_BRACKET)) {
                do {
                    els.add(expression());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_BRACKET, "Expected ']' after list literal");
            return new Expr.ArrayLiteral(bracket, els);
        }

        if (match(TokenType.LEFT_BRACE)) {
            Token brace = previous();
            List<Expr.MapEntry> entries = new ArrayList<>();
            if (!check(TokenType.RIGHT_BRACE)) {
                do {
                    Expr key = expression();
                    consume(TokenType.COLON, "Expected ':' after map key");
                    Expr val = expression();
                    entries.add(new Expr.MapEntry(key, val));
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_BRACE, "Expected '}' after map literal");
            return new Expr.MapLiteral(brace, entries);
        }

        throw error(peek(), "Expected expression");
    }

    private void optionalSemicolon() {
        match(TokenType.SEMICOLON);
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return type == TokenType.EOF;
        return peek().type == type;
    }

    private boolean checkNext(TokenType type) {
        if (current + 1 >= tokens.size()) return false;
        return tokens.get(current + 1).type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseException error(Token token, String message) {
        return new ParseException(message + " at " + token.line + ":" + token.column);
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;
            switch (peek().type) {
                case LET, CONST, STRUCT, CLASS, ENUM, IF, WHILE, FOR, SWITCH, DEFER, FUNC, RETURN, TRY, IMPORT, BREAK, CONTINUE -> {
                    return;
                }
                default -> {
                }
            }
            advance();
        }
    }

    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
