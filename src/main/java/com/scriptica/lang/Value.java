package com.scriptica.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Value {
    public static final Value NULL = new Value(null);

    private final Object value;

    public Value(Object value) {
        this.value = value;
    }

    public Object raw() {
        return value;
    }

    public boolean isNull() {
        return value == null;
    }

    public boolean isNumber() {
        return value instanceof Double;
    }

    public boolean isString() {
        return value instanceof String;
    }

    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    public boolean isCallable() {
        return value instanceof ScripticaCallable;
    }

    public boolean isList() {
        return value instanceof List;
    }

    public boolean isMap() {
        return value instanceof Map;
    }

    public boolean isTask() {
        return value instanceof ScriptTask;
    }

    public double asNumber() {
        if (value instanceof Double d) return d;
        throw new ScripticaRuntimeException("Expected number, got " + typeName());
    }

    public boolean asBoolean() {
        if (value instanceof Boolean b) return b;
        return !isNull();
    }

    public String asString() {
        if (value == null) return "null";
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public List<Value> asList() {
        if (value instanceof List<?> list) {
            return (List<Value>) list;
        }
        throw new ScripticaRuntimeException("Expected list, got " + typeName());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Value> asMap() {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Value>) map;
        }
        throw new ScripticaRuntimeException("Expected map, got " + typeName());
    }

    public ScriptTask asTask() {
        if (value instanceof ScriptTask t) return t;
        throw new ScripticaRuntimeException("Expected task, got " + typeName());
    }

    public ScripticaCallable asCallable() {
        if (value instanceof ScripticaCallable c) return c;
        throw new ScripticaRuntimeException("Expected function, got " + typeName());
    }

    public String typeName() {
        if (value == null) return "null";
        if (value instanceof Double) return "number";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof ScripticaCallable) return "function";
        if (value instanceof List) return "list";
        if (value instanceof Map) return "map";
        if (value instanceof ScriptTask) return "task";
        return value.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        if (value instanceof Double d) {
            if (d == Math.rint(d)) return String.valueOf(d.longValue());
            return String.valueOf(d);
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.valueOf(list.get(i)));
            }
            sb.append(']');
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(String.valueOf(e.getKey())).append(": ").append(String.valueOf(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Value other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @SuppressWarnings("unchecked")
    public static Value of(Object o) {
        if (o == null) return NULL;
        if (o instanceof Value v) return v;
        if (o instanceof Integer i) return new Value(i.doubleValue());
        if (o instanceof Long l) return new Value(l.doubleValue());
        if (o instanceof Float f) return new Value(f.doubleValue());
        if (o instanceof Double d) return new Value(d);
        if (o instanceof Boolean b) return new Value(b);
        if (o instanceof String s) return new Value(s);
        if (o instanceof ScripticaCallable c) return new Value(c);
        if (o instanceof ScriptTask t) return new Value(t);
        if (o instanceof List<?> list) {
            List<Value> out = new ArrayList<>();
            for (Object item : list) out.add(Value.of(item));
            return new Value(out);
        }
        if (o instanceof Map<?, ?> map) {
            Map<String, Value> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), Value.of(e.getValue()));
            }
            return new Value(out);
        }
        throw new ScripticaRuntimeException("Unsupported value type: " + o.getClass().getName());
    }
}
