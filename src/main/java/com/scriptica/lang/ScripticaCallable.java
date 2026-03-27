package com.scriptica.lang;

import java.util.List;

public interface ScripticaCallable {
    int arity();

    default int minArity() {
        return arity();
    }

    default int maxArity() {
        return arity();
    }

    Value call(Interpreter interpreter, List<Value> args);
}
