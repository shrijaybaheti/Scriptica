package com.scriptica.lang;

import java.util.HashMap;
import java.util.Map;

public final class Environment {
    private final Environment enclosing;
    private final Map<String, Binding> values = new HashMap<>();

    public Environment() {
        this(null);
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public void define(String name, Value value) {
        define(name, value, false);
    }

    public void define(String name, Value value, boolean isConst) {
        values.put(name, new Binding(value, isConst));
    }

    public Value get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme).value;
        }
        if (enclosing != null) return enclosing.get(name);
        throw new ScripticaRuntimeException("Undefined variable '" + name.lexeme + "'");
    }

    public void assign(Token name, Value value) {
        if (values.containsKey(name.lexeme)) {
            Binding b = values.get(name.lexeme);
            if (b.isConst) throw new ScripticaRuntimeException("Cannot reassign const '" + name.lexeme + "'");
            b.value = value;
            return;
        }
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        throw new ScripticaRuntimeException("Undefined variable '" + name.lexeme + "'");
    }

    private static final class Binding {
        Value value;
        final boolean isConst;

        Binding(Value value, boolean isConst) {
            this.value = value;
            this.isConst = isConst;
        }
    }
}
