# Examples

## Grass miner (demo)
Mines nearby grass blocks (`minecraft:grass_block`) by finding the nearest block, aiming at it, and holding attack.

```sca
print("[Scriptica] Grass miner running. Press Stop to cancel.");

while (true) {
  // Find nearest grass block within 5 blocks of you
  let b = nearestBlock("minecraft:grass_block", 5);
  if (b == null) {
    wait(10);
    continue;
  }

  // Mine it (aim + hold attack)
  mineBlock(b.x, b.y, b.z, 18);
  wait(2);
}
```

## A simple key macro
Walk forward for 2 seconds.

```sca
press("forward", 40);
```

## Event example
Print your coordinates every second (20 ticks).

```sca
onTick(func() {
  // Keep work small in tick handlers.
});

every(20, func() {
  print("pos=" + posX() + ", " + posY() + ", " + posZ());
});
```
