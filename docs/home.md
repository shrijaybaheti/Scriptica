<div class="hero">
  <div class="hero-left">
    <div class="hero-badge">Minecraft Fabric • Client-side</div>
    <h1>Scriptica</h1>
    <p class="hero-tagline">
      Write scripts <strong>in-game</strong> to automate common Minecraft tasks — movement macros, world interaction, and event-driven helpers.
    </p>
    <div class="hero-actions">
      <a class="btn primary" href="#/install">Install</a>
      <a class="btn" href="#/mod-usage">Get Started</a>
      <a class="btn ghost" href="https://github.com/shrijaybaheti/Scriptica" target="_blank" rel="noreferrer">GitHub</a>
    </div>
    <div class="hero-meta">
      <span>Target: <code>1.21.11</code></span>
      <span>Hotkey: <code>`</code></span>
      <span>Scripts: <code>.sca</code></span>
    </div>
  </div>

  <div class="hero-right">
    <div class="card-grid">
      <div class="card">
        <div class="card-title">In‑game IDE</div>
        <div class="card-text">Edit, save, load, run, stop — without alt-tabbing.</div>
      </div>
      <div class="card">
        <div class="card-title">Automation</div>
        <div class="card-text">Keys, look/turn, attack/use, inventory helpers.</div>
      </div>
      <div class="card">
        <div class="card-title">World Interaction</div>
        <div class="card-text">Raycast, find blocks/entities, mine/place helpers.</div>
      </div>
      <div class="card">
        <div class="card-title">Events</div>
        <div class="card-text">Tick, chat, custom events — build reactive scripts.</div>
      </div>
    </div>
  </div>
</div>

## 30‑second demo

Mine nearby grass blocks:

```sca
print("[Scriptica] Grass miner running. Press Stop to cancel.");

while (true) {
  let b = nearestBlock("minecraft:grass_block", 5);
  if (b == null) { wait(10); continue; }

  mineBlock(b.x, b.y, b.z, 18);
  wait(2);
}
```

## Next steps
- Install: `install.md`
- Using the mod: `mod-usage.md`
- Examples: `examples.md`
- Language reference: `scriptica-language.md`
