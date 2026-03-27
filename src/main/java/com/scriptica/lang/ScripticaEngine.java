package com.scriptica.lang;

import java.util.List;

public final class ScripticaEngine {
	public void run(String source, ScripticaHost host, CancellationToken token) {
		List<Token> tokens = new Lexer(source).scanTokens();
		List<Ast.Stmt> program = new Parser(tokens).parse();
		new Interpreter(host, token).execute(program);
	}
}

