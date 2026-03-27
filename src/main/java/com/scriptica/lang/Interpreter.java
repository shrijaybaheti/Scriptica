package com.scriptica.lang;

import com.scriptica.lang.Ast.Expr;
import com.scriptica.lang.Ast.Stmt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public final class Interpreter {
    private static final int MAX_STEPS = 500_000;

    private final ScripticaHost host;
    private final CancellationToken token;

    private final Set<String> included = new HashSet<>();

    private Environment globals;
    private Environment env;
    private int steps = 0;

    private Random random = new Random();

    private final ArrayDeque<List<Stmt.Block>> deferStack = new ArrayDeque<>();

    public Interpreter(ScripticaHost host, CancellationToken token) {
        this.host = Objects.requireNonNull(host, "host");
        this.token = Objects.requireNonNull(token, "token");
        this.globals = new Environment();
        this.env = globals;
        installBuiltins();
    }

    public void execute(List<Stmt> program) {
        executeBlock(program, env);
    }

    private void installBuiltins() {

        globals.define("print", Value.of(new NativeFn(1, args -> {
            host.log(args.get(0).toString());
            return Value.NULL;
        })), true);

        globals.define("log", globals.get(new Token(TokenType.IDENTIFIER, "print", null, 0, 0)), true);

        globals.define("debug", Value.of(new NativeFn(1, args -> {
            host.log("[debug] " + args.get(0));
            return Value.NULL;
        })), true);

        globals.define("assert", Value.of(new NativeFn(2, args -> {
            boolean ok = isTruthy(args.get(0));
            if (!ok) {
                String msg = args.get(1).toString();
                throw new ScripticaRuntimeException("Assertion failed: " + msg);
            }
            return Value.NULL;
        })), true);

        globals.define("chat", Value.of(new NativeFn(1, args -> {
            host.chat(args.get(0).toString());
            return Value.NULL;
        })), true);

        globals.define("cmd", Value.of(new NativeFn(1, args -> {
            host.command(args.get(0).toString());
            return Value.NULL;
        })), true);

        globals.define("wait", Value.of(new NativeFn(1, args -> {
            long ticks = (long) Math.max(0, Math.round(args.get(0).asNumber()));
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            }
            return Value.NULL;
        })), true);

        globals.define("timeMs", Value.of(new NativeFn(0, args -> Value.of((double) System.currentTimeMillis()))), true);

        globals.define("randSeed", Value.of(new NativeFn(1, args -> {
            long seed = (long) Math.round(args.get(0).asNumber());
            random = new Random(seed);
            return Value.NULL;
        })), true);

        globals.define("rand", Value.of(new NativeFn(2, args -> {
            double a = args.get(0).asNumber();
            double b = args.get(1).asNumber();
            double min = Math.min(a, b);
            double max = Math.max(a, b);
            double r = min + (random.nextDouble() * (max - min));
            return Value.of(r);
        })), true);

        globals.define("len", Value.of(new NativeFn(1, args -> {
            Value v = args.get(0);
            if (v.isString()) return Value.of((double) v.asString().length());
            if (v.isList()) return Value.of((double) v.asList().size());
            if (v.isMap()) return Value.of((double) v.asMap().size());
            return Value.of(0.0);
        })), true);

        globals.define("push", Value.of(new NativeFn(2, args -> {
            List<Value> list = args.get(0).asList();
            list.add(args.get(1));
            return Value.NULL;
        })), true);

        globals.define("pop", Value.of(new NativeFn(1, args -> {
            List<Value> list = args.get(0).asList();
            if (list.isEmpty()) return Value.NULL;
            return list.remove(list.size() - 1);
        })), true);

        globals.define("keys", Value.of(new NativeFn(1, args -> {
            Map<String, Value> map = args.get(0).asMap();
            List<Value> out = new ArrayList<>();
            for (String k : map.keySet()) out.add(Value.of(k));
            return new Value(out);
        })), true);

        globals.define("hasKey", Value.of(new NativeFn(2, args -> {
            Map<String, Value> map = args.get(0).asMap();
            String key = args.get(1).toString();
            return Value.of(map.containsKey(key));
        })), true);

        globals.define("delKey", Value.of(new NativeFn(2, args -> {
            Map<String, Value> map = args.get(0).asMap();
            String key = args.get(1).toString();
            Value v = map.remove(key);
            return v == null ? Value.NULL : v;
        })), true);

        globals.define("clone", Value.of(new NativeFn(1, args -> deepClone(args.get(0)))), true);

        globals.define("sort", Value.of(new NativeFn(1, args -> {
            List<Value> list = args.get(0).asList();
            list.sort((a, b) -> {
                if (a.isNumber() && b.isNumber()) return Double.compare(a.asNumber(), b.asNumber());
                return a.toString().compareTo(b.toString());
            });
            return new Value(list);
        })), true);

        globals.define("include", Value.of(new NativeFn(1, args -> {
            String name = args.get(0).toString();
            includeScript(name);
            return Value.NULL;
        })), true);

        globals.define("waitUntil", Value.of(new NativeFn(2, args -> {
            ScripticaCallable pred = args.get(0).asCallable();
            long timeout = (long) Math.max(0, Math.round(args.get(1).asNumber()));
            long elapsed = 0;
            while (elapsed < timeout) {
                token.checkCancelled();
                Value v = pred.call(this, List.of());
                if (isTruthy(v)) return Value.of(true);
                try {
                    host.sleepTicks(1, token);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ScripticaRuntimeException("Interrupted");
                }
                elapsed++;
            }
            return Value.of(false);
        })), true);

        globals.define("after", Value.of(new NativeFn(2, args -> {
            long ticks = (long) Math.max(0, Math.round(args.get(0).asNumber()));
            ScripticaCallable fn = args.get(1).asCallable();
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            }
            return fn.call(this, List.of());
        })), true);


        // --- Types / strings / math ---

        globals.define("type", Value.of(new NativeFn(1, args -> Value.of(args.get(0).typeName()))), true);

        globals.define("str", Value.of(new NativeFn(1, args -> Value.of(args.get(0).toString()))), true);

        globals.define("num", Value.of(new NativeFn(1, args -> {
            Value v = args.get(0);
            if (v.isNumber()) return v;
            if (v.isBoolean()) return Value.of(v.asBoolean() ? 1.0 : 0.0);
            String s = v.toString().trim();
            if (s.isEmpty()) return Value.of(0.0);
            try {
                return Value.of(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return Value.of(0.0);
            }
        })), true);

        globals.define("int", Value.of(new NativeFn(1, args -> Value.of((double) ((long) Math.rint(args.get(0).asNumber()))))), true);
        globals.define("floor", Value.of(new NativeFn(1, args -> Value.of(Math.floor(args.get(0).asNumber())))), true);
        globals.define("ceil", Value.of(new NativeFn(1, args -> Value.of(Math.ceil(args.get(0).asNumber())))), true);
        globals.define("abs", Value.of(new NativeFn(1, args -> Value.of(Math.abs(args.get(0).asNumber())))), true);
        globals.define("min", Value.of(new NativeFn(2, args -> Value.of(Math.min(args.get(0).asNumber(), args.get(1).asNumber())))), true);
        globals.define("max", Value.of(new NativeFn(2, args -> Value.of(Math.max(args.get(0).asNumber(), args.get(1).asNumber())))), true);
        globals.define("clamp", Value.of(new NativeFn(3, args -> {
            double v = args.get(0).asNumber();
            double lo = args.get(1).asNumber();
            double hi = args.get(2).asNumber();
            return Value.of(Math.max(Math.min(v, Math.max(lo, hi)), Math.min(lo, hi)));
        })), true);
        globals.define("atan2", Value.of(new NativeFn(2, args -> Value.of(Math.toDegrees(Math.atan2(args.get(0).asNumber(), args.get(1).asNumber()))))), true);

        globals.define("lower", Value.of(new NativeFn(1, args -> Value.of(args.get(0).toString().toLowerCase()))), true);
        globals.define("upper", Value.of(new NativeFn(1, args -> Value.of(args.get(0).toString().toUpperCase()))), true);
        globals.define("trim", Value.of(new NativeFn(1, args -> Value.of(args.get(0).toString().trim()))), true);
        globals.define("contains", Value.of(new NativeFn(2, args -> Value.of(args.get(0).toString().contains(args.get(1).toString())))), true);
        globals.define("startsWith", Value.of(new NativeFn(2, args -> Value.of(args.get(0).toString().startsWith(args.get(1).toString())))), true);
        globals.define("endsWith", Value.of(new NativeFn(2, args -> Value.of(args.get(0).toString().endsWith(args.get(1).toString())))), true);
        globals.define("replace", Value.of(new NativeFn(3, args -> Value.of(args.get(0).toString().replace(args.get(1).toString(), args.get(2).toString())))), true);
        globals.define("substr", Value.of(new NativeFn(3, args -> {
            String s = args.get(0).toString();
            int start = (int) Math.rint(args.get(1).asNumber());
            int len = (int) Math.rint(args.get(2).asNumber());
            if (start < 0) start = Math.max(0, s.length() + start);
            start = Math.max(0, Math.min(start, s.length()));
            int end = Math.max(start, Math.min(s.length(), start + Math.max(0, len)));
            return Value.of(s.substring(start, end));
        })), true);

        globals.define("split", Value.of(new NativeFn(2, args -> {
            String s = args.get(0).toString();
            String sep = args.get(1).toString();
            List<Value> out = new ArrayList<>();
            if (sep.isEmpty()) {
                for (int i = 0; i < s.length(); i++) out.add(Value.of(String.valueOf(s.charAt(i))));
                return new Value(out);
            }
            for (String part : s.split(java.util.regex.Pattern.quote(sep), -1)) out.add(Value.of(part));
            return new Value(out);
        })), true);

        globals.define("joinStr", Value.of(new NativeFn(2, args -> {
            List<Value> list = args.get(0).asList();
            String sep = args.get(1).toString();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(sep);
                sb.append(list.get(i));
            }
            return Value.of(sb.toString());
        })), true);

        // --- Ranges ---

        globals.define("range", Value.of(new NativeFn(2, args -> makeRange(args.get(0), args.get(1)))) , true);

        // --- JSON ---

        globals.define("jsonEncode", Value.of(new NativeFn(1, args -> Value.of(jsonEncode(args.get(0))))), true);
        globals.define("jsonDecode", Value.of(new NativeFn(1, args -> jsonDecode(args.get(0).toString()))), true);

        // --- Tasks / async ---

        globals.define("spawn", Value.of(new NativeFn(1, args -> spawnTask(args.get(0).asCallable()))), true);
        globals.define("join", Value.of(new NativeFn(1, args -> joinTask(args.get(0).asTask()))), true);
        globals.define("cancel", Value.of(new NativeFn(1, args -> {
            cancelTask(args.get(0).asTask());
            return Value.NULL;
        })), true);
        globals.define("done", Value.of(new NativeFn(1, args -> Value.of(args.get(0).asTask().done))), true);

        // --- Events ---

        globals.define("onTick", Value.of(new NativeFn(1, args -> {
            ScripticaCallable fn = args.get(0).asCallable();
            return Value.of(host.onTick(fn, token));
        })), true);

        globals.define("onChat", Value.of(new NativeFn(1, args -> {
            ScripticaCallable fn = args.get(0).asCallable();
            return Value.of(host.onChat(fn, token));
        })), true);

        globals.define("on", Value.of(new NativeFn(2, args -> {
            String name = args.get(0).toString();
            ScripticaCallable fn = args.get(1).asCallable();
            return Value.of(host.on(name, fn, token));
        })), true);

        globals.define("emit", Value.of(new NativeFn(2, args -> {
            host.emit(args.get(0).toString(), args.get(1));
            return Value.NULL;
        })), true);

        globals.define("off", Value.of(new NativeFn(1, args -> {
            host.off(args.get(0).asNumber());
            return Value.NULL;
        })), true);

        globals.define("every", Value.of(new NativeFn(2, args -> {
            long ticks = (long) Math.max(0, Math.round(args.get(0).asNumber()));
            ScripticaCallable fn = args.get(1).asCallable();
            return spawnRepeating(ticks, fn);
        })), true);

        globals.define("waitMs", Value.of(new NativeFn(1, args -> {
            long ms = (long) Math.max(0, Math.round(args.get(0).asNumber()));
            long ticks = (ms + 49L) / 50L;
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            }
            return Value.NULL;
        })), true);
        // --- Automation built-ins ---

        globals.define("posX", Value.of(new NativeFn(0, args -> Value.of(host.playerX()))), true);
        globals.define("posY", Value.of(new NativeFn(0, args -> Value.of(host.playerY()))), true);
        globals.define("posZ", Value.of(new NativeFn(0, args -> Value.of(host.playerZ()))), true);
        globals.define("yaw", Value.of(new NativeFn(0, args -> Value.of(host.playerYaw()))), true);
        globals.define("pitch", Value.of(new NativeFn(0, args -> Value.of(host.playerPitch()))), true);
        globals.define("health", Value.of(new NativeFn(0, args -> Value.of(host.health()))), true);
        globals.define("hunger", Value.of(new NativeFn(0, args -> Value.of(host.hunger()))), true);
        globals.define("armor", Value.of(new NativeFn(0, args -> Value.of(host.armor()))), true);

        globals.define("onGround", Value.of(new NativeFn(0, args -> Value.of(host.onGround()))), true);
        globals.define("sneaking", Value.of(new NativeFn(0, args -> Value.of(host.sneaking()))), true);
        globals.define("sprinting", Value.of(new NativeFn(0, args -> Value.of(host.sprinting()))), true);

        globals.define("dimension", Value.of(new NativeFn(0, args -> Value.of(host.dimension()))), true);
        globals.define("heldItem", Value.of(new NativeFn(0, args -> Value.of(host.heldItem()))), true);
        globals.define("offhandItem", Value.of(new NativeFn(0, args -> Value.of(host.offhandItem()))), true);

        globals.define("attackHold", Value.of(new NativeFn(1, args -> {
            long ticks = (long) Math.max(0, Math.round(args.get(0).asNumber()));
            host.setKey("attack", true);
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            } finally {
                host.setKey("attack", false);
            }
            return Value.NULL;
        })), true);

        globals.define("useHold", Value.of(new NativeFn(1, args -> {
            long ticks = (long) Math.max(0, Math.round(args.get(0).asNumber()));
            host.setKey("use", true);
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            } finally {
                host.setKey("use", false);
            }
            return Value.NULL;
        })), true);

        globals.define("look", Value.of(new NativeFn(2, args -> {
            host.look(args.get(0).asNumber(), args.get(1).asNumber());
            return Value.NULL;
        })), true);

        globals.define("turn", Value.of(new NativeFn(2, args -> {
            host.turn(args.get(0).asNumber(), args.get(1).asNumber());
            return Value.NULL;
        })), true);

        globals.define("key", Value.of(new NativeFn(2, args -> {
            String name = args.get(0).toString();
            Value v = args.get(1);
            boolean down;
            if (v.isBoolean()) down = v.asBoolean();
            else if (v.isNumber()) down = v.asNumber() != 0;
            else if (v.isNull()) down = false;
            else down = !v.toString().isEmpty();
            host.setKey(name, down);
            return Value.NULL;
        })), true);

        globals.define("press", Value.of(new NativeFn(2, args -> {
            String name = args.get(0).toString();
            long ticks = (long) Math.max(0, Math.round(args.get(1).asNumber()));
            host.setKey(name, true);
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            } finally {
                host.setKey(name, false);
            }
            return Value.NULL;
        })), true);

        globals.define("releaseKeys", Value.of(new NativeFn(0, args -> {
            host.releaseAllKeys();
            return Value.NULL;
        })), true);

        globals.define("attack", Value.of(new NativeFn(0, args -> {
            host.attack();
            return Value.NULL;
        })), true);

        globals.define("use", Value.of(new NativeFn(0, args -> {
            host.use();
            return Value.NULL;
        })), true);

        globals.define("hotbar", Value.of(new NativeFn(1, args -> {
            int slot = (int) Math.round(args.get(0).asNumber());
            host.hotbar(slot);
            return Value.NULL;
        })), true);
        globals.define("raycast", Value.of(new NativeFn(1, args -> Value.of(host.raycast(args.get(0).asNumber())))), true);
        globals.define("blockAt", Value.of(new NativeFn(3, args -> {
            int x = (int) Math.rint(args.get(0).asNumber());
            int y = (int) Math.rint(args.get(1).asNumber());
            int z = (int) Math.rint(args.get(2).asNumber());
            return Value.of(host.blockAt(x, y, z));
        })), true);
        globals.define("entities", Value.of(new NativeFn(2, args -> {
            double radius = args.get(0).asNumber();
            String filter = args.get(1).isNull() ? "" : args.get(1).toString();
            return Value.of(host.entities(radius, filter));
        })), true);
        globals.define("nearestEntity", Value.of(new NativeFn(2, args -> {
            double radius = args.get(0).asNumber();
            String filter = args.get(1).isNull() ? "" : args.get(1).toString();
            List<Object> ents = host.entities(radius, filter);
            if (ents == null || ents.isEmpty()) return Value.NULL;
            Object best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (Object o : ents) {
                Value v = Value.of(o);
                if (!v.isMap()) continue;
                Value d = v.asMap().get("distance");
                double dd = d == null || !d.isNumber() ? Double.POSITIVE_INFINITY : d.asNumber();
                if (dd < bestD) {
                    bestD = dd;
                    best = o;
                }
            }
            return Value.of(best);
        })), true);
        globals.define("findBlocks", Value.of(new NativeFn(3, args -> {
            String id = args.get(0).toString();
            int radius = (int) Math.rint(args.get(1).asNumber());
            int max = (int) Math.rint(args.get(2).asNumber());
            return Value.of(host.findBlocks(id, radius, max));
        })), true);

        globals.define("nearestBlock", Value.of(new NativeFn(2, args -> {
            String id = args.get(0).toString();
            int radius = (int) Math.rint(args.get(1).asNumber());
            return Value.of(host.nearestBlock(id, radius));
        })), true);

        globals.define("entity", Value.of(new NativeFn(1, args -> {
            int id = (int) Math.rint(args.get(0).asNumber());
            return Value.of(host.entity(id));
        })), true);

        globals.define("attackEntity", Value.of(new NativeFn(1, args -> {
            int id = (int) Math.rint(args.get(0).asNumber());
            return Value.of(host.attackEntity(id));
        })), true);

        globals.define("useEntity", Value.of(new NativeFn(1, args -> {
            int id = (int) Math.rint(args.get(0).asNumber());
            return Value.of(host.useEntity(id));
        })), true);

        globals.define("invCount", Value.of(new NativeFn(1, args -> Value.of(host.invCount(args.get(0).toString())))), true);
        globals.define("invSelect", Value.of(new NativeFn(1, args -> Value.of(host.invSelect(args.get(0).toString())))), true);

        globals.define("invFind", Value.of(new NativeFn(1, args -> Value.of(host.invFind(args.get(0).toString())))), true);
        globals.define("invSelectSlot", Value.of(new NativeFn(1, args -> Value.of(host.invSelectSlot((int) Math.rint(args.get(0).asNumber()))))), true);

        globals.define("attackBlock", Value.of(new NativeFn(4, args -> {
            int x = (int) Math.rint(args.get(0).asNumber());
            int y = (int) Math.rint(args.get(1).asNumber());
            int z = (int) Math.rint(args.get(2).asNumber());
            String side = args.get(3).toString();
            return Value.of(host.attackBlock(x, y, z, side));
        })), true);

        globals.define("useOnBlock", Value.of(new NativeFn(4, args -> {
            int x = (int) Math.rint(args.get(0).asNumber());
            int y = (int) Math.rint(args.get(1).asNumber());
            int z = (int) Math.rint(args.get(2).asNumber());
            String side = args.get(3).toString();
            return Value.of(host.useOnBlock(x, y, z, side));
        })), true);        globals.define("invSlot", Value.of(new NativeFn(1, args -> {
            int slot = (int) Math.rint(args.get(0).asNumber());
            return Value.of(host.invSlot(slot));
        })), true);

        globals.define("lookAt", Value.of(new NativeFn(3, args -> {
            lookAt(args.get(0).asNumber(), args.get(1).asNumber(), args.get(2).asNumber());
            return Value.NULL;
        })), true);

        globals.define("walkTo", Value.of(new NativeFn(4, args -> {
            double x = args.get(0).asNumber();
            double y = args.get(1).asNumber();
            double z = args.get(2).asNumber();
            long timeout = (long) Math.max(0, Math.round(args.get(3).asNumber()));
            return Value.of(walkTo(x, y, z, timeout));
        })), true);
        globals.define("mineBlock", Value.of(new NativeFn(4, args -> {
            double x = args.get(0).asNumber();
            double y = args.get(1).asNumber();
            double z = args.get(2).asNumber();
            long ticks = (long) Math.max(0, Math.round(args.get(3).asNumber()));
            lookAt(x + 0.5, y + 0.5, z + 0.5);
            host.setKey("attack", true);
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            } finally {
                host.setKey("attack", false);
            }
            return Value.NULL;
        })), true);

        globals.define("placeBlock", Value.of(new NativeFn(4, args -> {
            double x = args.get(0).asNumber();
            double y = args.get(1).asNumber();
            double z = args.get(2).asNumber();
            long ticks = (long) Math.max(0, Math.round(args.get(3).asNumber()));
            lookAt(x + 0.5, y + 0.5, z + 0.5);
            host.setKey("use", true);
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            } finally {
                host.setKey("use", false);
            }
            return Value.NULL;
        })), true);
    }

    private void includeScript(String name) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty()) throw new ScripticaRuntimeException("include: empty name");
        if (included.contains(key)) return;
        included.add(key);

        String src = host.loadScript(key);
        if (src == null) throw new ScripticaRuntimeException("include: script not found: " + key);

        List<Token> tokens = new Lexer(src).scanTokens();
        List<Stmt> program = new Parser(tokens).parse();
        for (Stmt s : program) exec(s);
    }

    private void exec(Stmt stmt) {
        token.checkCancelled();
        steps++;
        if (steps > MAX_STEPS) throw new ScripticaRuntimeException("Step limit exceeded (possible infinite loop)");

        if (stmt instanceof Stmt.Expression s) {
            eval(s.expression());
            return;
        }
        if (stmt instanceof Stmt.Let s) {
            Value value = s.initializer() == null ? Value.NULL : eval(s.initializer());
            env.define(s.name().lexeme, value, false);
            return;
        }
        if (stmt instanceof Stmt.Const s) {
            Value value = s.initializer() == null ? Value.NULL : eval(s.initializer());
            env.define(s.name().lexeme, value, true);
            return;
        }
        if (stmt instanceof Stmt.Destructure s) {
            Value src = eval(s.source());
            if (!src.isMap()) throw new ScripticaRuntimeException("Destructuring requires map value");
            Map<String, Value> m = src.asMap();
            for (Token n : s.names()) {
                Value v = m.getOrDefault(n.lexeme, Value.NULL);
                env.define(n.lexeme, v, s.isConst());
            }
            return;
        }
        if (stmt instanceof Stmt.Block s) {
            executeBlock(s.statements(), new Environment(env));
            return;
        }
        if (stmt instanceof Stmt.If s) {
            if (isTruthy(eval(s.condition()))) {
                exec(s.thenBranch());
            } else if (s.elseBranch() != null) {
                exec(s.elseBranch());
            }
            return;
        }
        if (stmt instanceof Stmt.While s) {
            while (isTruthy(eval(s.condition()))) {
                try {
                    exec(s.body());
                } catch (ContinueSignal ignored) {
                    // continue
                } catch (BreakSignal ignored) {
                    break;
                }
                token.checkCancelled();
                steps++;
                if (steps > MAX_STEPS) throw new ScripticaRuntimeException("Step limit exceeded (possible infinite loop)");
            }
            return;
        }
        if (stmt instanceof Stmt.ForEach s) {
            execForEach(s);
            return;
        }
        if (stmt instanceof Stmt.Defer s) {
            // Defer runs when the current block exits
            if (!deferStack.isEmpty()) {
                deferStack.peek().add(s.block());
            }
            return;
        }
        if (stmt instanceof Stmt.Switch s) {
            execSwitch(s);
            return;
        }
        if (stmt instanceof Stmt.Struct s) {
            List<String> fields = new ArrayList<>();
            for (Token f : s.fields()) fields.add(f.lexeme);
            env.define(s.name().lexeme, Value.of(new StructCtor(s.name().lexeme, fields)), true);
            return;
        }
        if (stmt instanceof Stmt.Enum s) {
            Map<String, Value> out = new LinkedHashMap<>();
            double next = 0.0;
            for (Stmt.EnumMember m : s.members()) {
                Value v;
                if (m.value() != null) v = eval(m.value());
                else v = Value.of(next);
                out.put(m.name().lexeme, v);
                if (v.isNumber()) next = v.asNumber() + 1.0;
                else next = next + 1.0;
            }
            env.define(s.name().lexeme, new Value(out), true);
            return;
        }
        if (stmt instanceof Stmt.Function s) {
            env.define(s.name().lexeme, Value.of(new UserFn(s, env)), true);
            return;
        }
        if (stmt instanceof Stmt.Return s) {
            Value v = s.value() == null ? Value.NULL : eval(s.value());
            throw new ReturnSignal(v);
        }
        if (stmt instanceof Stmt.Wait s) {
            long ticks = (long) Math.max(0, Math.round(eval(s.ticks()).asNumber()));
            try {
                host.sleepTicks(ticks, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            }
            return;
        }
        if (stmt instanceof Stmt.Break) {
            throw new BreakSignal();
        }
        if (stmt instanceof Stmt.Continue) {
            throw new ContinueSignal();
        }
        if (stmt instanceof Stmt.TryCatch s) {
            try {
                exec(s.tryBlock());
            } catch (ScripticaRuntimeException e) {
                if ("Cancelled".equals(e.getMessage())) throw e;
                Environment catchEnv = new Environment(env);
                catchEnv.define(s.errorName().lexeme, Value.of(e.getMessage()), true);
                executeBlock(s.catchBlock().statements(), catchEnv);
            }
            return;
        }
        if (stmt instanceof Stmt.Import s) {
            String spec = eval(s.specifier()).toString();
            includeScript(spec);
            return;
        }

        throw new ScripticaRuntimeException("Unknown statement: " + stmt.getClass().getName());
    }


    private void runDefers(List<Stmt.Block> defers) {
        for (int i = defers.size() - 1; i >= 0; i--) {
            Stmt.Block b = defers.get(i);
            for (Stmt s : b.statements()) {
                exec(s);
            }
        }
    }
    void executeBlock(List<Stmt> statements, Environment nextEnv) {
        Environment previous = this.env;
        List<Stmt.Block> defers = new ArrayList<>();
        deferStack.push(defers);
        try {
            this.env = nextEnv;
            for (Stmt statement : statements) {
                exec(statement);
            }
        } finally {
            // Run defers while this scope environment is still active.
            try {
                runDefers(deferStack.pop());
            } finally {
                this.env = previous;
            }
        }
    }

    private Value eval(Expr expr) {
        token.checkCancelled();

        if (expr instanceof Expr.Literal e) return Value.of(e.value());
        if (expr instanceof Expr.Template e) return Value.of(evalTemplate(e.raw(), e.token()));
        if (expr instanceof Expr.Grouping e) return eval(e.expression());
        if (expr instanceof Expr.Variable e) return env.get(e.name());
        if (expr instanceof Expr.Assign e) {
            Value value = eval(e.value());
            env.assign(e.name(), value);
            return value;
        }
        if (expr instanceof Expr.ArrayLiteral e) {
            List<Value> out = new ArrayList<>();
            for (Expr el : e.elements()) out.add(eval(el));
            return new Value(out);
        }
        if (expr instanceof Expr.MapLiteral e) {
            Map<String, Value> out = new LinkedHashMap<>();
            for (Expr.MapEntry entry : e.entries()) {
                String k = eval(entry.key()).toString();
                out.put(k, eval(entry.value()));
            }
            return new Value(out);
        }
        if (expr instanceof Expr.Get e) {
            Value target = eval(e.target());
            if (!target.isMap()) throw new ScripticaRuntimeException("Dot access requires map target");
            Value v = target.asMap().getOrDefault(e.name().lexeme, Value.NULL);
            if (v.isCallable()) {
                ScripticaCallable fn = v.asCallable();
                if (fn.maxArity() >= 1) return Value.of(new BoundFn(target, fn));
            }
            return v;
        }
        if (expr instanceof Expr.Set e) {
            Value target = eval(e.target());
            if (!target.isMap()) throw new ScripticaRuntimeException("Dot set requires map target");
            Value v = eval(e.value());
            target.asMap().put(e.name().lexeme, v);
            return v;
        }

        if (expr instanceof Expr.Index e) {
            Value target = eval(e.target());
            Value idx = eval(e.index());
            return indexGet(target, idx);
        }
        if (expr instanceof Expr.IndexAssign e) {
            Value target = eval(e.target());
            Value idx = eval(e.index());
            Value v = eval(e.value());
            indexSet(target, idx, v);
            return v;
        }
        if (expr instanceof Expr.Unary e) {
            Value right = eval(e.right());
            return switch (e.operator().type) {
                case BANG -> Value.of(!isTruthy(right));
                case MINUS -> Value.of(-right.asNumber());
                default -> throw new ScripticaRuntimeException("Invalid unary operator: " + e.operator().lexeme);
            };
        }
        if (expr instanceof Expr.Ternary e) {
            if (isTruthy(eval(e.condition()))) return eval(e.thenExpr());
            return eval(e.elseExpr());
        }
        if (expr instanceof Expr.Range e) {
            return makeRange(eval(e.start()), eval(e.end()));
        }

        if (expr instanceof Expr.Logical e) {
            Value left = eval(e.left());
            if (e.operator().type == TokenType.OR_OR) {
                if (isTruthy(left)) return left;
            } else {
                if (!isTruthy(left)) return left;
            }
            return eval(e.right());
        }
        if (expr instanceof Expr.Binary e) {
            Value left = eval(e.left());
            Value right = eval(e.right());
            return switch (e.operator().type) {
                case PLUS -> plus(left, right);
                case MINUS -> Value.of(left.asNumber() - right.asNumber());
                case STAR -> Value.of(left.asNumber() * right.asNumber());
                case SLASH -> Value.of(left.asNumber() / right.asNumber());
                case PERCENT -> Value.of(left.asNumber() % right.asNumber());
                case GREATER -> Value.of(left.asNumber() > right.asNumber());
                case GREATER_EQUAL -> Value.of(left.asNumber() >= right.asNumber());
                case LESS -> Value.of(left.asNumber() < right.asNumber());
                case LESS_EQUAL -> Value.of(left.asNumber() <= right.asNumber());
                case EQUAL_EQUAL -> Value.of(equalsValue(left, right));
                case BANG_EQUAL -> Value.of(!equalsValue(left, right));
                default -> throw new ScripticaRuntimeException("Invalid binary operator: " + e.operator().lexeme);
            };
        }
        if (expr instanceof Expr.Call e) {
            Value callee = eval(e.callee());
            List<Value> args = new ArrayList<>(e.arguments().size());
            for (Expr arg : e.arguments()) args.add(eval(arg));
            ScripticaCallable fn = callee.asCallable();
            int minA = fn.minArity();
            int maxA = fn.maxArity();
            if (args.size() < minA || args.size() > maxA) {
                throw new ScripticaRuntimeException("Expected " + minA + ".." + maxA + " args, got " + args.size());
            }
            return fn.call(this, args);
        }

        throw new ScripticaRuntimeException("Unknown expression: " + expr.getClass().getName());
    }



    private void execSwitch(Stmt.Switch s) {
        Value switchVal = eval(s.value());
        boolean executing = false;
        boolean anyMatched = false;

        for (Stmt.SwitchCase c : s.cases()) {
            if (!executing) {
                if (c.isDefault()) {
                    if (!anyMatched) executing = true;
                } else if (caseMatches(c.match(), switchVal)) {
                    executing = true;
                    anyMatched = true;
                }
            }

            if (!executing) continue;

            try {
                executeBlock(c.statements(), new Environment(env));
            } catch (BreakSignal ignored) {
                break;
            }
        }
    }

    private boolean caseMatches(Expr matchExpr, Value switchVal) {
        if (matchExpr instanceof Expr.Variable v) {
            if ("_".equals(v.name().lexeme)) return true;
        }
        if (matchExpr instanceof Expr.Range r && switchVal.isNumber()) {
            double start = eval(r.start()).asNumber();
            double end = eval(r.end()).asNumber();
            double x = switchVal.asNumber();
            double lo = Math.min(start, end);
            double hi = Math.max(start, end);
            return x >= lo && x < hi;
        }
        Value caseVal = eval(matchExpr);
        return equalsValue(switchVal, caseVal);
    }

    private void execForEach(Stmt.ForEach s) {
        Value it = eval(s.iterable());
        List<Value> items = toIterableValues(it);

        for (Value item : items) {
            Environment iterEnv = new Environment(env);
            iterEnv.define(s.varName().lexeme, item, false);
            try {
                executeBlock(List.of(s.body()), iterEnv);
            } catch (ContinueSignal ignored) {
                // continue
            } catch (BreakSignal ignored) {
                break;
            }

            token.checkCancelled();
            steps++;
            if (steps > MAX_STEPS) throw new ScripticaRuntimeException("Step limit exceeded (possible infinite loop)");
        }
    }

    private static List<Value> toIterableValues(Value it) {
        if (it.isList()) return it.asList();
        if (it.isMap()) {
            List<Value> out = new ArrayList<>();
            for (String k : it.asMap().keySet()) out.add(Value.of(k));
            return out;
        }
        if (it.isString()) {
            String s = it.asString();
            List<Value> out = new ArrayList<>(s.length());
            for (int i = 0; i < s.length(); i++) out.add(Value.of(String.valueOf(s.charAt(i))));
            return out;
        }
        if (it.isNumber()) {
            return makeRange(Value.of(0.0), it).asList();
        }
        throw new ScripticaRuntimeException("Not iterable: " + it.typeName());
    }

    private String evalTemplate(String raw, Token token) {
        if (raw == null || raw.isEmpty()) return "";

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int start = findInterpolationStart(raw, i);
            if (start < 0) {
                out.append(raw.substring(i));
                break;
            }
            out.append(raw, i, start);
            int end = findInterpolationEnd(raw, start + 2);
            if (end < 0) {
                throw new ScripticaRuntimeException("Unterminated ${} in template at " + token.line + ":" + token.column);
            }
            String exprSrc = raw.substring(start + 2, end);
            try {
                Expr expr = new Parser(new Lexer(exprSrc).scanTokens()).parseExpression();
                out.append(eval(expr));
            } catch (RuntimeException e) {
                throw new ScripticaRuntimeException("Template error: " + e.getMessage());
            }
            i = end + 1;
        }
        return out.toString().replace("\\${", "${");
    }

    private static int findInterpolationStart(String raw, int from) {
        int i = from;
        while (i < raw.length() - 1) {
            int idx = raw.indexOf("${", i);
            if (idx < 0) return -1;
            if (idx > 0 && raw.charAt(idx - 1) == '\\') {
                i = idx + 2;
                continue;
            }
            return idx;
        }
        return -1;
    }

    private static int findInterpolationEnd(String raw, int from) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = from; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (c == '\\' && quote != 96) {
                    if (i + 1 < raw.length()) i++;
                    continue;
                }
                if (c == quote) {
                    inString = false;
                }
                continue;
            }

            if (c == 34 || c == 96) {
                inString = true;
                quote = c;
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    private static Value makeRange(Value startV, Value endV) {
        long start = (long) Math.rint(startV.asNumber());
        long end = (long) Math.rint(endV.asNumber());
        long step = start <= end ? 1 : -1;

        // end is exclusive
        long count = step > 0 ? Math.max(0, end - start) : Math.max(0, start - end);
        if (count > 100_000) throw new ScripticaRuntimeException("Range too large: " + count);

        List<Value> out = new ArrayList<>((int) count);
        long v = start;
        for (long n = 0; n < count; n++) {
            out.add(Value.of((double) v));
            v += step;
        }
        return new Value(out);
    }

    private static String jsonEncode(Value v) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonElement el = valueToJson(v);
        return gson.toJson(el);
    }

    private static Value jsonDecode(String json) {
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
            return jsonToValue(el);
        } catch (RuntimeException e) {
            throw new ScripticaRuntimeException("jsonDecode: " + e.getMessage());
        }
    }

    private static com.google.gson.JsonElement valueToJson(Value v) {
        if (v.isNull()) return com.google.gson.JsonNull.INSTANCE;
        if (v.isBoolean()) return new com.google.gson.JsonPrimitive(v.asBoolean());
        if (v.isNumber()) return new com.google.gson.JsonPrimitive(v.asNumber());
        if (v.isString()) return new com.google.gson.JsonPrimitive(v.asString());
        if (v.isList()) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (Value item : v.asList()) arr.add(valueToJson(item));
            return arr;
        }
        if (v.isMap()) {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (Map.Entry<String, Value> e : v.asMap().entrySet()) obj.add(e.getKey(), valueToJson(e.getValue()));
            return obj;
        }
        return new com.google.gson.JsonPrimitive(v.toString());
    }

    private static Value jsonToValue(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) return Value.NULL;
        if (el.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return Value.of(p.getAsBoolean());
            if (p.isNumber()) return Value.of(p.getAsDouble());
            if (p.isString()) return Value.of(p.getAsString());
            return Value.of(p.getAsString());
        }
        if (el.isJsonArray()) {
            List<Value> out = new ArrayList<>();
            for (com.google.gson.JsonElement item : el.getAsJsonArray()) out.add(jsonToValue(item));
            return new Value(out);
        }
        if (el.isJsonObject()) {
            Map<String, Value> out = new LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : el.getAsJsonObject().entrySet()) out.put(e.getKey(), jsonToValue(e.getValue()));
            return new Value(out);
        }
        return Value.of(el.toString());
    }

    private Value spawnTask(ScripticaCallable fn) {
        CancellationToken child = new CancellationToken(token);
        ScriptTask task = new ScriptTask(child);

        Thread t = new Thread(() -> {
            try {
                Interpreter childInterpreter = new Interpreter(host, child);
                task.result = fn.call(childInterpreter, List.of());
            } catch (ScripticaRuntimeException e) {
                task.error = e;
            } catch (RuntimeException e) {
                task.error = new ScripticaRuntimeException(String.valueOf(e.getMessage()));
            } finally {
                task.done = true;
            }
        }, "scriptica-task");
        task.thread = t;
        t.start();
        return Value.of(task);
    }

    private Value spawnRepeating(long ticks, ScripticaCallable fn) {
        CancellationToken child = new CancellationToken(token);
        ScriptTask task = new ScriptTask(child);

        Thread t = new Thread(() -> {
            try {
                Interpreter childInterpreter = new Interpreter(host, child);
                while (!child.isCancelled()) {
                    fn.call(childInterpreter, List.of());
                    try {
                        host.sleepTicks(ticks, child);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ScripticaRuntimeException("Interrupted");
                    }
                }
            } catch (ScripticaRuntimeException e) {
                task.error = e;
            } catch (RuntimeException e) {
                task.error = new ScripticaRuntimeException(String.valueOf(e.getMessage()));
            } finally {
                task.done = true;
            }
        }, "scriptica-every");
        task.thread = t;
        t.start();
        return Value.of(task);
    }

    private static Value joinTask(ScriptTask task) {
        Thread t = task.thread;
        if (t != null) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScripticaRuntimeException("Interrupted");
            }
        }
        if (task.error != null) throw task.error;
        return task.result == null ? Value.NULL : task.result;
    }

    private static void cancelTask(ScriptTask task) {
        task.token.cancel();
        Thread t = task.thread;
        if (t != null) t.interrupt();
    }

    private void lookAt(double x, double y, double z) {
        double px = host.playerX();
        double py = host.playerY() + 1.62;
        double pz = host.playerZ();

        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 1e-6) distXZ = 1e-6;

        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, distXZ));
        host.look(yaw, pitch);
    }

    private boolean walkTo(double x, double y, double z, long timeoutTicks) {
        long ticks = 0;
        host.setKey("forward", true);
        try {
            while (ticks < timeoutTicks) {
                token.checkCancelled();

                double px = host.playerX();
                double pz = host.playerZ();
                double dx = x - px;
                double dz = z - pz;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 0.75) return true;

                double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                host.look(yaw, host.playerPitch());

                try {
                    host.sleepTicks(1, token);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ScripticaRuntimeException("Interrupted");
                }
                ticks++;
            }
            return false;
        } finally {
            host.setKey("forward", false);
        }
    }

    private static Value indexGet(Value target, Value idx) {
        if (target.isList()) {
            int i = (int) Math.round(idx.asNumber());
            List<Value> list = target.asList();
            if (i < 0 || i >= list.size()) return Value.NULL;
            return list.get(i);
        }
        if (target.isMap()) {
            String k = idx.toString();
            Map<String, Value> map = target.asMap();
            return map.getOrDefault(k, Value.NULL);
        }
        if (target.isString()) {
            int i = (int) Math.round(idx.asNumber());
            String s = target.asString();
            if (i < 0 || i >= s.length()) return Value.NULL;
            return Value.of(String.valueOf(s.charAt(i)));
        }
        throw new ScripticaRuntimeException("Indexing not supported on " + target.typeName());
    }

    private static void indexSet(Value target, Value idx, Value value) {
        if (target.isList()) {
            int i = (int) Math.round(idx.asNumber());
            List<Value> list = target.asList();
            if (i == list.size()) {
                list.add(value);
                return;
            }
            if (i < 0 || i >= list.size()) throw new ScripticaRuntimeException("List index out of range: " + i);
            list.set(i, value);
            return;
        }
        if (target.isMap()) {
            String k = idx.toString();
            target.asMap().put(k, value);
            return;
        }
        throw new ScripticaRuntimeException("Index assignment not supported on " + target.typeName());
    }

    private static Value deepClone(Value v) {
        if (v == null || v.isNull()) return Value.NULL;
        if (v.isList()) {
            List<Value> out = new ArrayList<>();
            for (Value it : v.asList()) out.add(deepClone(it));
            return new Value(out);
        }
        if (v.isMap()) {
            Map<String, Value> out = new LinkedHashMap<>();
            for (Map.Entry<String, Value> e : v.asMap().entrySet()) {
                out.put(e.getKey(), deepClone(e.getValue()));
            }
            return new Value(out);
        }
        return v;
    }


    private static Value plus(Value left, Value right) {
        if (left.isNumber() && right.isNumber()) return Value.of(left.asNumber() + right.asNumber());
        return Value.of(left.toString() + right.toString());
    }

    private static boolean equalsValue(Value a, Value b) {
        if (a.isNull() && b.isNull()) return true;
        if (a.isNull() || b.isNull()) return false;
        return a.equals(b);
    }

    private static boolean isTruthy(Value v) {
        if (v.isNull()) return false;
        if (v.isBoolean()) return v.asBoolean();
        return true;
    }


    private static final class StructCtor implements ScripticaCallable {
        private final String typeName;
        private final List<String> fields;

        private StructCtor(String typeName, List<String> fields) {
            this.typeName = typeName;
            this.fields = fields;
        }

        @Override
        public int arity() {
            return fields.size();
        }

        @Override
        public Value call(Interpreter interpreter, List<Value> args) {
            Map<String, Value> out = new LinkedHashMap<>();
            out.put("__type", Value.of(typeName));
            for (int i = 0; i < fields.size(); i++) {
                out.put(fields.get(i), args.get(i));
            }
            return new Value(out);
        }
    }

    private static final class BoundFn implements ScripticaCallable {
        private final Value receiver;
        private final ScripticaCallable fn;

        private BoundFn(Value receiver, ScripticaCallable fn) {
            this.receiver = receiver;
            this.fn = fn;
        }

        @Override
        public int arity() {
            return maxArity();
        }

        @Override
        public int minArity() {
            return Math.max(0, fn.minArity() - 1);
        }

        @Override
        public int maxArity() {
            return Math.max(0, fn.maxArity() - 1);
        }

        @Override
        public Value call(Interpreter interpreter, List<Value> args) {
            List<Value> out = new ArrayList<>(args.size() + 1);
            out.add(receiver);
            out.addAll(args);
            return fn.call(interpreter, out);
        }
    }
    private static final class NativeFn implements ScripticaCallable {
        private final int arity;
        private final Function<List<Value>, Value> impl;

        private NativeFn(int arity, Function<List<Value>, Value> impl) {
            this.arity = arity;
            this.impl = impl;
        }

        @Override
        public int arity() {
            return arity;
        }

        @Override
        public Value call(Interpreter interpreter, List<Value> args) {
            return impl.apply(args);
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Value value;

        ReturnSignal(Value value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private static final class BreakSignal extends RuntimeException {
        BreakSignal() {
            super(null, null, false, false);
        }
    }

    private static final class ContinueSignal extends RuntimeException {
        ContinueSignal() {
            super(null, null, false, false);
        }
    }

    private static final class UserFn implements ScripticaCallable {
        private final Stmt.Function decl;
        private final Environment closure;

        private UserFn(Stmt.Function decl, Environment closure) {
            this.decl = decl;
            this.closure = closure;
        }

        @Override
        public int arity() {
            return maxArity();
        }

        @Override
        public int minArity() {
            int min = 0;
            for (Stmt.Param p : decl.params()) {
                if (p.defaultValue() == null) min++;
                else break;
            }
            return min;
        }

        @Override
        public int maxArity() {
            return decl.params().size();
        }

        @Override
        public Value call(Interpreter interpreter, List<Value> args) {
            Environment fnEnv = new Environment(closure);
            Environment prev = interpreter.env;
            try {
                interpreter.env = fnEnv;
                for (int i = 0; i < decl.params().size(); i++) {
                    Stmt.Param p = decl.params().get(i);
                    Value v;
                    if (i < args.size()) {
                        v = args.get(i);
                    } else if (p.defaultValue() != null) {
                        v = interpreter.eval(p.defaultValue());
                    } else {
                        v = Value.NULL;
                    }
                    fnEnv.define(p.name().lexeme, v, false);
                }

                try {
                    interpreter.executeBlock(decl.body().statements(), fnEnv);
                } catch (ReturnSignal r) {
                    return r.value;
                }
                return Value.NULL;
            } finally {
                interpreter.env = prev;
            }
        }
    }
}

















