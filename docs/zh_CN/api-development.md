# API 与附属插件开发

## 引入 API

Maven：

```xml
<dependency>
    <groupId>com.simpmc.bedwars</groupId>
    <artifactId>simpmc-bedwars-api</artifactId>
    <version>4.1.9</version>
    <scope>provided</scope>
</dependency>
```

附属插件的 `plugin.yml` 应声明：

```yaml
depend: [SimpMC-BedWars]
```

## 获取服务

```java
BedWars api = Bukkit.getServicesManager()
        .load(com.andrei1058.bedwars.api.BedWars.class);
if (api == null) {
    throw new IllegalStateException("SimpMC-BedWars API 未注册");
}
```

不要强制转换为插件内部的 `com.andrei1058.bedwars.API`。

## 安全查询竞技场

```java
api.getArenaUtil().findArenaByPlayer(player).ifPresent(arena -> {
    player.sendMessage("当前地图：" + arena.getDisplayName());
});

for (IArena arena : api.getArenaUtil().getArenasSnapshot()) {
    // 这是不可变快照，不会修改插件内部注册表。
}
```

旧版 `getArenas()` 为兼容保留，但会暴露内部可变集合；新代码应使用 `getArenasSnapshot()`。

竞技场内的常用集合也提供只读快照：`getPlayersSnapshot()`、`getSpectatorsSnapshot()`、`getTeamsSnapshot()`、`getOreGeneratorsSnapshot()` 和 `getSignsSnapshot()`。旧的无 `Snapshot` 方法仍为二进制兼容保留，但附属插件不应修改它们返回的内部列表。

游戏进入 `playing` 后，可以区分全部配置队伍与本局实际参战队伍：

```java
List<ITeam> configuredTeams = arena.getTeams();
List<ITeam> participatingTeams = arena.getActiveTeamsAtGameStart();
ITeam firstTeam = participatingTeams.getFirst();
int playersAtStart = arena.getTeamSizeAtGameStart(firstTeam);
```

`getActiveTeamsAtGameStart()` 与 `getTeamSizeAtGameStart(ITeam)` 都是开局瞬间的快照，因此队伍后续被淘汰、玩家掉线或重连不会改变结果。第三方 `IArena` 实现若不覆盖这些方法，会分别返回全部配置队伍和队伍当前人数，以保持二进制兼容。

`IArena#getMinPlayers()` 返回整个竞技场进入正常倒计时所需的最低人数，默认值为 BedWars1058 的 `2`；`getMaxInTeam()` 返回每队容量。正常分配还必须产生至少两支非空且未超员的队伍，并优先填满最少数量的已启用队伍；预选队伍只作为映射到配置队伍时的偏好，不能因此额外启用队伍。`getMinInTeam()` 已弃用，仅保留默认返回 `1` 的源码/二进制兼容桥，不再参与匹配。`/bw start debug` 仍可绕过最低人数和双队限制，但不能绕过容量。等待人数达到 `getMaxPlayers()` 时，开局倒计时超过 5 秒会固定压缩为 5 秒；该运行时规则没有配置项。

3.0.0 起每个竞技场只属于一个匹配分组：

```java
String group = arena.getGroup();
boolean featured = arena.isInGroup("Featured");
arena.setGroup("Solo");
```

2.13.x 发布过的 `getGroups()`、`setGroups()` 以及 PlaceholderAPI 复数占位符继续存在，避免旧附属插件加载失败，但已弃用并只读写第一个组。新代码只使用单数 API。

## 开局邀请组队 API

```java
PreGameSquad squads = api.getPreGameSquadUtil();
PreGameSquad.Result result = squads.invite(inviter, target);

if (result == PreGameSquad.Result.SUCCESS) {
    inviter.sendMessage("邀请已创建");
}

List<Player> members = squads.getMembers(inviter); // 不可变快照
Player leader = squads.getLeader(inviter);
```

邀请仅在同一竞技场的 waiting/starting 阶段有效，默认 30 秒过期，人数不能超过 `maxInTeam`。游戏开始、竞技场重置、玩家退出或竞技场禁用时会自动清理。

## 计分板与 TAB API

```java
ISidebarService sidebars = api.getScoreboardManager();
IArena arenaSnapshot = api.getArenaUtil().findArenaByPlayer(player).orElse(null);
sidebars.giveSidebar(player, arenaSnapshot, false);
```

`giveSidebar` 的竞技场参数是调用时快照；实现会在应用前与实时玩家竞技场注册表核对，避免延迟任务把新竞技场面板覆盖成大厅面板。附属插件不应在竞技场内持续调用 `Player#setScoreboard` 与 BedWars 争抢同一面板；若只需扩展内容，应监听 `PlayerSidebarInitEvent` 操作公开的 `ISidebar`。SimpMC-BedWars 离开上下文时会恢复接管前最后观察到的外部 scoreboard。

TAB 玩家行会按查看者语言解析 `{teamName}`、`{teamLetter}` 和 `{teamColor}`；内置模板默认让游戏进行中的存活玩家及结算阶段仍有原队伍归属的玩家，在玩家名前显示带队伍颜色的完整队名。插件只向对应查看者发送“前缀 + 明确队伍色玩家名 + 后缀”的完整 PlayerInfo 显示名，不修改目标玩家的全局 `playerListName`；scoreboard Team 独立负责头顶名牌颜色、碰撞和隐身可见性。附属插件若向同一竞技场查看者持续覆盖 PlayerInfo 显示名，会覆盖 BedWars 的 TAB 文字，应在竞技场上下文中避免争抢该状态。

7.1.0 新增 `PlayerTab.PlayerListMode`。`ACTUAL` 保留目标当前真实模式；`SPECTATOR` 只向其他查看者发送 `UPDATE_GAME_MODE=SPECTATOR`，不会调用 `Player#setGameMode`，适合需要保留 ADVENTURE 交互的自定义旁观行。7.1.1 起，当目标就是 Sidebar 持有者本人时始终保留 Paper 当前真实模式；旧版本缓存过的本人伪模式会立即恢复，避免客户端移动模式与服务器分叉。旧构造器和旧 `Sidebar#playerTabCreate` 重载默认使用 `ACTUAL`；可通过带 `PlayerListMode` 的新重载创建，或对已有行调用 `setPlayerListMode`。Sidebar 释放、被覆盖或删除该行时会从 Paper 当前状态恢复真实模式和第三方 nullable 名称。

游戏进行中的 TAB 页首使用 `{gameTime}` 显示从 `IArena#getStartTime()` 计算的本局已进行时间；`{time}` 仍是下一事件倒计时。附属插件若直接使用 PlaceholderAPI，可读取 `%bw1058_elapsed_time%`，其格式与 TAB 一致：不足一小时为 `MM:SS`，一小时以上为 `HH:MM:SS`。开始时间缺失时返回空文本。

## 配置 API

```java
ConfigManager main = api.getConfigs().getMainConfig();
ConfigManager generators = api.getConfigs().getGeneratorsConfig();
ConfigManager shop = api.getConfigs().getShopConfig();
ConfigManager upgrades = api.getConfigs().getUpgradesConfig();
ConfigManager levels = api.getConfigs().getLevelsConfig();
ConfigManager rewards = api.getConfigs().getRewardsConfig();
ConfigManager sounds = api.getConfigs().getSoundsConfig();
```

这些对象在 SimpMC-BedWars 完成 `onEnable` 后可用。写配置时使用 `set`/`save`，不要直接操作磁盘上的 YAML。

8.0.0 新增 `ConfigPath.GENERAL_FIREBALL_SNEAK_ACCELERATION_MULTIPLIER`，用于读取 `fireball.sneak-acceleration-multiplier`。该值只调整潜行火球的持续加速度，不替代 `sneak-speed-multiplier` 初速度倍率。`VersionSupport#setFireballAcceleration` 是服务端版本适配入口，可在不覆盖当前初速度的前提下设置持续加速度；普通附属插件通常不应直接调用版本支持层。

需要在世界尚未加载时读取通用坐标中的世界名，可使用：

```java
String worldName = ConfigManager.getWorldNameFromConfigLocation(
        main.getYml().getString("lobbyLoc"));
```

该方法只解析配置文本，不调用 `Bukkit#getWorld`，适合插件加载阶段和世界加载器使用；无效或缺少世界名时抛出 `IllegalArgumentException`。

2.10.44 起可通过以下 API 使用与插件一致的方向规则：

```java
float yaw = ConfigManager.snapYawToCardinal(-88.328); // -90.0
String stored = ConfigManager.serializeConfigLocation(location);
```

`serializeConfigLocation` 会保留坐标和世界，把 yaw 吸附到最近的 90 度倍数，并把 pitch 固定为 0。`SubCommand#getTabComplete(CommandSender)` 可生成与发送者或设置会话相关的动态补全；旧扩展只实现无参方法仍保持兼容。

## 大厅状态

```java
api.getArenaUtil().applyLobbyState(player);
```

该方法应用冒险模式、飞行/拾取状态和大厅物品。调用前应确保玩家已经成功传送到配置的大厅世界。4.0.8 起，主线程调用会立即刷新，并在 15 tick 后执行一次受实时上下文保护的选择性复核；非主线程调用会安全转交主线程。正常大厅流程只移除旧的 BedWars 命令物品，并会拒绝已经进入竞技场、设置会话或离开真实大厅的过期调用。旧方法 `sendLobbyCommandItems` 为兼容既有附属插件，仍保持“延迟 15 tick、清空整个背包后再发放”的公开契约；新代码应优先在传送成功后调用 `applyLobbyState`。

## 常用事件

- `PlayerJoinArenaEvent` / `PlayerLeaveArenaEvent`
- `GameStateChangeEvent` / `GameEndEvent`
- `TeamAssignEvent`
- `PlayerFirstSpawnEvent` / `PlayerReSpawnEvent`
- `PlayerBedBreakEvent` / `PlayerKillEvent`
- `ShopBuyEvent` / `UpgradeBuyEvent`（商店事件的 `getPurchaseCount()` 可区分普通单次购买与 Shift+右键批量购买）
- `ArenaEnableEvent` / `ArenaDisableEvent`

示例：

```java
@EventHandler
public void onState(GameStateChangeEvent event) {
    if (event.getNewState() == GameState.playing) {
        getLogger().info(event.getArena().getArenaName() + " 已开始");
    }
}
```

## 自定义队伍分配器

`IArena#setTeamAssigner(ITeamAssigner)` 可替换单个竞技场的分配逻辑。实现必须遵守：

- 不超过 `arena.getMaxInTeam()`。
- 普通开局至少产生两支非空队伍，否则开局会被阻止。管理员执行 `/bw start debug` 时，`arena.getStartingTask().isSingleTeamDebugStart()` 返回 `true`，此时允许恰好一支非空队伍，但仍不得产生零队伍。
- 分配前调用 `TeamAssignEvent` 并尊重取消结果。
- 只在主线程修改 Bukkit 玩家和队伍。

## 队伍颜色 API

青色队伍使用 `TeamColor.CYAN`。读取管理员配置时建议调用 `TeamColor.fromName(value)`，它会把 2.10.11 以前的旧值 `AQUA` 安全规范为 `CYAN`。列出可创建颜色时使用 `TeamColor.selectableValues()`，避免展示为附属插件二进制兼容而保留的弃用别名 `AQUA`。

`TeamColor.CYAN.woolMaterial()` 返回 `Material.CYAN_WOOL`；Minecraft 没有独立的青色聊天代码，因此 `chat()` 仍返回最接近的 `ChatColor.AQUA`。

需要从地图方块识别队伍颜色时，使用 `TeamColor.fromWool(material)`。该方法只接受精确支持的羊毛材质，不做前缀、子字符串或近似颜色匹配；例如 `LIME_WOOL` 返回 `GREEN`，`GREEN_WOOL` 返回 `DARK_GREEN`，`LIGHT_GRAY_WOOL` 返回 `GRAY`，`GRAY_WOOL` 返回 `DARK_GRAY`。不支持的羊毛或非羊毛材质返回 `null`。`setupName()` 返回自动创建队伍时使用的稳定英文名称。

## 玩家放置方块 API

附属插件通过代码生成可破坏方块时，必须调用 `arena.addPlacedBlock(block)`；方块被插件移除时调用 `arena.removePlacedBlock(block)`。读取状态可使用：

```java
Set<Vector> placedBlocks = arena.getPlacedBlocksSnapshot();
```

返回值是与竞技场内部索引分离的只读快照。旧方法 `getPlaced()` 已弃用，修改其返回列表不会再改变竞技场状态；这样可以避免附属插件误把地图原生方块标记成玩家方块。所有 Bukkit 方块操作和所有权更新都应在主线程完成。

玩家通过正常 Bukkit 事件放置、破坏、炸毁、燃烧或移动方块时，插件会在下一 tick 按事件最终取消状态与真实 `BlockData` 对账所有权，兼容在 BedWars 的 `MONITOR` 之后才取消事件的反作弊/保护插件。附属插件直接调用 `addPlacedBlock`/`removePlacedBlock` 仍是即时操作，应在自身世界修改成功后调用。被正确登记的玩家方块优先于地图保护区域，可以由玩家或 BedWars 爆炸正常破坏；队伍床始终例外。

## API 兼容原则

- 只依赖 `simpmc-bedwars-api`，不要导入 `bedwars-plugin` 内部类。
- 对可能不存在的竞技场使用 `Optional` 查询。
- 不要修改旧版暴露的内部集合。
- Bukkit 实体操作在主线程执行。
- 监听竞技场禁用和玩家离开事件，及时清理自己的缓存。
