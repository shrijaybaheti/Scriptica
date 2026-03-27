# Scriptica Language

Scriptica is a scripting language embedded in the Scriptica Minecraft mod.

## Files
- Scripts are saved as `*.sca` under:
  - `.minecraft/config/scriptica/scripts/`

## Quick Example
```sca
print("Hello from Scriptica!");
wait(20);
chat("hi");
cmd("/time query daytime");
```

## Control Flow
- `if (...) { ... } else { ... }`
- `while (...) { ... }`
- `for (i in 0..10) { ... }` (end-exclusive)
- `for (init; cond; inc) { ... }`
- `break` exits the nearest loop
- `continue` skips to the next loop iteration
- Ternary: `cond ? a : b`
- `switch (x) { case 1: ... break; default: ... }`
- `try { ... } catch (e) { ... }` (`e` is the error message string)
- `defer { ... }` runs when the *current block* exits (LIFO)

## Strings
### Normal strings
- Use `"..."` strings with escapes: `\n`, `\t`, `\r`, `\"`, `\\`.

### Template strings
- Use backticks for templates: `` `hello ${1 + 2}` ``.
- Escape interpolation with `\${` (it becomes a literal `${`).

## OOP-lite: `struct`, `class`, `enum` + dot access

### `struct` / `class`
`struct` and `class` currently behave the same: they define a constructor function that returns a map.

```sca
struct Point { x, y };
let p = Point(2, 3);
print(p.x);

p.x = 10;
```

### `enum`
```sca
enum Color { RED, GREEN, BLUE };
print(Color.RED);   // 0
print(Color.GREEN); // 1
```

### Dot-call method binding
If a map field is a function with arity `(self, ...)`, calling it via dot will auto-bind `self`.

```sca
func inc(self) { self.x = self.x + 1; }
let o = {"x": 1, "inc": inc};
o.inc();
print(o.x); // 2
```

## Functions

### Default parameters
You can set defaults with `=`. Missing arguments use defaults (evaluated at call time).

```sca
func add(a, b = 2, c = 3) {
  return a + b + c;
}
print(add(1));     // 6
print(add(1, 10)); // 14
```

### Callbacks (no anonymous functions yet)
Scriptica does not currently support `func(...) { ... }` as an expression.
Define a named function and pass it:

```sca
let ticks = 0;
let h = null;
func tickCb() {
  ticks = ticks + 1;
  if (ticks >= 3) { off(h); }
}

h = onTick(tickCb);
```

## Destructuring
Unpack map fields into variables. Missing keys become `null`.

```sca
let m = {"a": 1};
let {a, b} = m;
print(a); // 1
print(b); // null
```

## Events
Events run callbacks on a separate thread.

- `onTick(fn)` -> handle (fn arity must be 0)
- `onChat(fn)` -> handle (fn arity must be 1: message)
- `on(name, fn)` -> handle (custom event; fn arity must be 1: payload)
- `emit(name, payload)`
- `off(handle)`

```sca
let got = 0;
func h(p) { got = p; }
let e = on("hello", h);
emit("hello", 7);
wait(2);
off(e);
print(got);
```

## Standard library (built-ins)

### Core
- `print(value)` / `log(value)`
- `debug(value)`
- `assert(cond, message)`
- `wait(ticks)` / `waitMs(ms)`
- `chat(text)` / `cmd(text)`
- `rand(min, max)`, `randSeed(seed)`
- `timeMs()`

### Data
- `len(x)`
- `push(list, value)`, `pop(list)`
- `joinStr(list, sep)`
- `keys(map)`
- `hasKey(map, key)`, `delKey(map, key)`
- `clone(value)` (deep clone maps/lists)
- `sort(list)` (in-place; returns list)
- `range(a, b)` and `a..b` (end-exclusive)
- `jsonEncode(value)`, `jsonDecode(string)`

### Tasks
- `spawn(fn)`, `join(task)`, `cancel(task)`, `done(task)`, `every(ticks, fn)`

## Automation (built-ins)

### Position / rotation
- `posX()`, `posY()`, `posZ()`
- `yaw()`, `pitch()`
- `look(yaw, pitch)`, `turn(dYaw, dPitch)`, `lookAt(x,y,z)`

### Keys / actions
- `key(name, down)`, `press(name, ticks)`, `releaseKeys()`
- `attack()`, `use()`, `hotbar(slot)`
- `attackHold(ticks)`, `useHold(ticks)`

### Player state
- `health()`, `hunger()`, `armor()`
- `onGround()`, `sneaking()`, `sprinting()`
- `dimension()`, `heldItem()`, `offhandItem()`

### World / inventory
- `raycast(maxDistance)`
- `blockAt(x,y,z)`
- `findBlocks(blockId, radius, maxResults)`
- `nearestBlock(blockId, radius)`
- `entity(id)`, `attackEntity(id)`, `useEntity(id)`
- `entities(radius, typeFilter)`, `nearestEntity(radius, typeFilter)`
- `invCount(itemId)`, `invSelect(itemId)`, `invSlot(slot)`
- `invFind(itemId)`
- `invSelectSlot(slot)`
- `attackBlock(x,y,z,side)`, `useOnBlock(x,y,z,side)`
- `mineBlock(x,y,z,ticks)` (aim + hold attack)
- `placeBlock(x,y,z,ticks)` (aim + hold use)

## Limits / Safety
- Scripts run on a separate thread and are cancelled when you press **Stop**.
- A step-limit is enforced to reduce runaway infinite loops.
- Minecraft actions are executed on the client thread.
- **Stop** releases keys to avoid getting stuck walking.
