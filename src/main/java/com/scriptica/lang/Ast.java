package com.scriptica.lang;

import java.util.List;

public final class Ast {
    private Ast() {}

    public interface Expr {
        record Literal(Object value) implements Expr {}

        /**
         * Backtick string with ${...} interpolation. The raw text is the string literal content (without quotes).
         */
        record Template(Token token, String raw) implements Expr {}

        record Variable(Token name) implements Expr {}

        record Assign(Token name, Expr value) implements Expr {}

        record Get(Expr target, Token name) implements Expr {}

        record Set(Expr target, Token name, Expr value) implements Expr {}

        record Unary(Token operator, Expr right) implements Expr {}

        record Binary(Expr left, Token operator, Expr right) implements Expr {}

        record Logical(Expr left, Token operator, Expr right) implements Expr {}

        record Ternary(Expr condition, Expr thenExpr, Expr elseExpr) implements Expr {}

        record Grouping(Expr expression) implements Expr {}

        record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr {}

        record ArrayLiteral(Token bracket, List<Expr> elements) implements Expr {}

        record MapEntry(Expr key, Expr value) {}

        record MapLiteral(Token brace, List<MapEntry> entries) implements Expr {}

        record Index(Expr target, Token bracket, Expr index) implements Expr {}

        record IndexAssign(Expr target, Token bracket, Expr index, Expr value) implements Expr {}

        record Range(Expr start, Token op, Expr end) implements Expr {}
    }

    public interface Stmt {
        record Expression(Expr expression) implements Stmt {}

        record Let(Token name, Expr initializer) implements Stmt {}

        record Const(Token name, Expr initializer) implements Stmt {}

        record Destructure(boolean isConst, List<Token> names, Expr source) implements Stmt {}

        record Struct(Token keyword, Token name, boolean isClass, List<Token> fields) implements Stmt {}

        record Enum(Token keyword, Token name, List<EnumMember> members) implements Stmt {}

        record EnumMember(Token name, Expr value) {}

        record Block(List<Stmt> statements) implements Stmt {}

        record If(Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt {}

        record While(Expr condition, Stmt body) implements Stmt {}

        record ForEach(Token varName, Expr iterable, Stmt body) implements Stmt {}

        record Switch(Token keyword, Expr value, List<SwitchCase> cases) implements Stmt {}

        record SwitchCase(Expr match, boolean isDefault, List<Stmt> statements) {}

        record Defer(Token keyword, Stmt.Block block) implements Stmt {}

        record Param(Token name, Expr defaultValue) {}

        record Function(Token name, List<Param> params, Stmt.Block body) implements Stmt {}

        record Return(Token keyword, Expr value) implements Stmt {}

        record Wait(Token keyword, Expr ticks) implements Stmt {}

        record Break(Token keyword) implements Stmt {}

        record Continue(Token keyword) implements Stmt {}

        record TryCatch(Stmt.Block tryBlock, Token errorName, Stmt.Block catchBlock) implements Stmt {}

        record Import(Token keyword, Expr specifier) implements Stmt {}
    }
}

