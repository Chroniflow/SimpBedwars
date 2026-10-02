# 全部配置文件说明

插件会把管理员可编辑的 YAML 放在 `plugins/SimpMC-BedWars`。`config-version` 由迁移器管理，不要手动降低。新版本会自动备份、补字段并写入中文注释。

`config-version` 是各配置文件的内部架构字段，不是商店分类、升级项或竞技场内容。2.10.16 起各加载器会主动忽略该字段。

2.10.50 起，自动迁移会以新版默认配置为结构模板：`config-version` 放在顶部，已知键按新版顺序放回对应配置节，不再统一追加到文件末尾。管理员或附属插件增加的未知键不会被删除，会留在所属配置节的已知键之后，其相对顺序、配置值和注释均保留。每次架构升级前仍会先生成 `.bak` 备份。

## config.yml

主配置，包含：

- `serverType`：MULTIARENA、SHARED 或 BUNGEE。
- BUNGEE 节点通过 `bungee-settings.node-role` 分为 `LOBBY` 和 `ARENA`。`LOBBY` 不加载本地竞技场，只监听 `lobby-listen.host/port`、维护远程目录并调度玩家；`ARENA` 加载地图、自动复制副本并向大厅上报状态。未填写角色时默认为 `ARENA`，以兼容旧版 BUNGEE。
- BUNGEE 子服的 `server-id` 必须全网唯一；`proxy-server` 是代理 `[servers]` 中的后端键名，`lobby-sockets` 是 ARENA 连接大厅的 `主机:端口` 列表，不能与大厅监听地址混用。`arena-template` 指定单个 `Arenas/<地图>.yml`，留空才保留旧版加载全部地图的行为。
- `bwp-time-out` 是 ARENA 子服预加载玩家等待代理登录的超时时间，单位为毫秒，默认 23000。插件会自动保证它至少覆盖大厅 `dispatch-timeout-seconds` 加 15 秒代理切服余量，旧配置中的 5000 等较小值无需手动修改。
- `debug`：详细故障日志开关，默认 `false`；生产环境保持关闭。`/bw start debug` 仅表示单队测试开局，不会修改此项或临时开启日志。
- `lobbyServer`：BungeeCord/Velocity 代理 `[servers]` 中的大厅服务器键名，缺失时默认 `hub`；不是 IP、端口或 MotD。如果代理配置为 `login = "地址:端口"`，这里必须填写 `login`。自动升级、排序、保存和再次启动都会保留管理员已有的值，不会将其重置为 `hub`。主大厅红床会立即静默发送 `Connect`，不查询或向玩家展示代理节点、服务器列表和故障信息；配置问题只通过后端及代理日志排查。
- `arenaGroups`：全局可用的匹配组名称；`Default` 是内置组，无需写入。每张竞技场地图只能选择其中一个组。
- `language`：固定使用简体中文 `zh_cn`；旧版本的语言值会自动迁移，历史禁用语言列表仅为兼容保留。
- `chat`：全局聊天和插件聊天格式。
- `scoreboard-settings`：大厅/游戏计分板、TAB、队伍颜色、血量和刷新周期。
- `rejoin-time`：普通断线或网络超时的重连窗口，默认 30 秒；服务器命令、封禁或反作弊造成的踢出不会保留重连资格。
- `countdowns`：开局、床消失、决战、结束和复活倒计时。
- `party-settings`：内部及外部组队集成。
- `tnt-jump-settings`、`blast-protection`、`tnt-prime-settings`：TNT 参数。`damage-others: 6` 是玩家 TNT 对敌方玩家的固定伤害（5.3.0 由 10 降低），`knockback-multiplier: 0.6` 按倍率缩放玩家 TNT 对对局内玩家的原版爆炸击退（1.0 为原版，范围 0 到 3），TNT 跳跃自身的力度公式不受该倍率影响。
- `fireball`：火球速度、射程、爆炸、击退、冷却和伤害。`speed-multiplier: 15` 对应普通初速度 1.5 格/tick；潜行时 `sneak-speed-multiplier: 1.6` 使初速度达到 2.4 格/tick，形成明显的初速度差异。`sneak-acceleration-multiplier: 2.0` 保持不变，持续加速度仍由普通火球的每 tick 0.1 提高到潜行火球的 0.2。每次发射会在 `flight-range.min: 200` 与 `flight-range.max: 300` 之间随机一次最大飞行距离，并按实际路径累计；碰撞、世界边界、服务端视距和未加载区块仍可能让火球提前结束。`sneak-recoil: 0.10` 会沿火球发射速度的完整三维反方向推动玩家，代码硬限制最大为 0.20；`cooldown: 0.4` 的持续射速约为每秒 2.5 发，一个 1 秒窗口内通常可发射 2 至 3 个。`explosion-size` 是以火球位置为球心的三维半径，伤害和击退只对欧氏距离不超过该半径的玩家生效，不会覆盖外接立方体的角落；队友始终不会受到火球伤害，但仍会按原有规则受到击退。当前默认值为爆炸范围 3.25、水平击退 1.35、垂直击退 0.75、敌方伤害 3.5；5.3.0 起水平击退沿水平面单独归一化，不会因玩家与爆炸中心的高度差而减弱。`make-fire` 只决定爆炸处是否生成火焰，竞技场不会允许火势向周围蔓延。
- `database`：MySQL；关闭时使用 SQLite。
- `match-statistics`：按对局保存统计和事件，默认启用。配置了可用的 MySQL 时使用共享连接池和共享对局编号；未启用 MySQL 时使用本地 `Cache/matches.db`。已配置 MySQL 但连接失败会明确标记统计未启用，不会静默切换到本地编号。默认时区为 `Asia/Shanghai`，上报间隔默认 300 秒（5 分钟）。MySQL 数据写入 `bw_matches`、`bw_match_players`、`bw_match_events`、`bw_match_reports` 和 `bw_player_violation_totals`，均使用 InnoDB 短事务；SQLite 使用同一组逻辑表保存本服数据。`bw_player_match_summary` 是按已结束对局汇总的只读视图，可直接用于大厅排行榜和玩家比较；若数据库账户没有 `CREATE VIEW` 权限，明细表仍会正常工作。
- `performance-settings`：Paper 传送、资源旋转等优化。
- `lobby-items`、`pre-game-items`、`spectator-items`：不同阶段的命令物品。主大厅默认提供历史战绩、竞技场选择器和第 9 格的“回到主大厅”红床。所有命令物品都执行配置中的命令；默认红床执行 `bw leave`，无需额外离场权限。MULTIARENA/SHARED 模式下，竞技场玩家先返回本服 BedWars 大厅，从大厅再次离开才连接代理的 `lobbyServer`；BUNGEE 模式直接连接代理大厅。5.7.0 起，本服大厅缺失或其世界未加载时，备用大厅也能直接连接代理大厅。4.0.8 起，删除整个物品节点后，后续配置升级不会再次生成；旧架构 15 曾误删的自定义 `leave` 会在当前值仍为内置默认值时，从架构 15 删除前的最后快照，或架构 15–17 中重新配置过的最新 `config.yml.v*.bak` 自动恢复；架构 18 后的删除或改写快照会否决旧值。进入或跨世界到达大厅后会刷新大厅物品；无效物品只跳过自身，同槽位配置会输出中文警告。完整代理示例见[安装文档](installation.md#bungee)。
- 大厅进入/离开提示只向同样位于 BedWars 大厅的玩家发送；竞技场、观战者和地图设置会话不会收到。大厅世界名直接从 `lobbyLoc` 文本读取，即使该世界在插件加载时尚未加载也能正确识别。大厅和加入 NPC 的旧朝向会自动迁移为最近的 90 度 yaw，pitch 固定为 0。
- 竞技场加入通知只在目标竞技场处于等待或开局倒计时发送，接收者为该竞技场与大厅玩家；已开局和重置阶段不播报。玩家在广播前即从大厅受众移除，不会因异步传送时序重复收到大厅身份消息。
- `arena-gui`、`stats-gui`：竞技场选择和历史战绩菜单。竞技场选择器默认使用 54 格、每页展示 45 张地图，更多地图通过底行按钮分页；旧版 27 格、7 槽位布局会自动扩展。自定义布局继续生效，但当当前尺寸连一页竞技场都放不下时，运行时至少扩展到 54 格，并始终保留可靠的上一页、页码和下一页导航。
- `start-items-per-group`：每个竞技场分组的默认开局物品。
- `allowed-commands`：游戏内允许的命令。
- `game-end`：淘汰玩家和最佳数据展示。

刷新周期通常以 tick 为单位；20 tick 约等于 1 秒。倒计时和重连时间以秒为单位。

### BUNGEE 角色配置

拆分部署时大厅与竞技场使用同一个插件 JAR、不同配置文件。大厅只需要代理大厅和数据库信息，并开启 `node-role: LOBBY`：

```yaml
serverType: BUNGEE
bungee-settings:
  node-role: LOBBY
  server-id: bw-lobby-01
  lobby-listen:
    host: 10.0.0.10
    port: 2019
  socket-secret: "与所有 ARENA 子服相同的随机长字符串"
```

竞技场子服设置 `node-role: ARENA`、唯一 `server-id`、代理后端键名和一张地图模板；同一模板可由 `auto-scale-clone-limit` 自动复制多个运行实例：

```yaml
serverType: BUNGEE
bungee-settings:
  node-role: ARENA
  server-id: bw-arena-castle-01
  proxy-server: bw-arena-castle-01
  arena-template: castle
  auto-scale-clone-limit: 5
  lobby-sockets:
    - 10.0.0.10:2019
  socket-secret: "与大厅相同的随机长字符串"
```

大厅目录和预约目前保存在单个大厅进程内存中，因此建议只运行一个活动大厅；如果要多活大厅，必须在外部增加 Redis 或 MySQL TTL 租约并替换预约协调器。套接字是内网明文 TCP，生产环境必须设置共享密钥并用防火墙限制来源。完整代理、端口和启动顺序见[安装文档](installation.md#bungee)。

MySQL 连接配置示例（所有 BUNGEE 子服使用同一个数据库）：

```yaml
database:
  enable: true
  host: 127.0.0.1
  port: 3306
  database: simpmc_bedwars
  user: bedwars
  pass: "请替换为实际密码"
  ssl: true
```

建议为插件创建独立数据库用户，并只授予目标库的 `SELECT`、`INSERT`、`UPDATE`、`CREATE`、`ALTER`、`INDEX` 权限；自动汇总视图需要额外的 `CREATE VIEW` 权限，没有该权限时明细表和对局写入仍会工作。最终 JAR 已内置 MySQL Connector/J，不需要再把驱动单独放进 Paper 的 `lib` 或 `plugins` 目录。修改连接信息后必须完整重启所有相关子服，不要使用 `/reload`。

对局统计配置示例：

```yaml
match-statistics:
  enabled: true
  timezone: Asia/Shanghai
  report-interval-seconds: 300
  queue-capacity: 10000
  retry-delay-seconds: 5
  finish-grace-ticks: 40
  violations:
    enabled: true
    warning-thresholds: [10, 20, 50, 100]
    match-leave-threshold: 25
    cross-team-item-transfer: true
```

每场实际开始的比赛都会生成永久唯一的 `match_uuid`，数据库分配持久化递增的 `match_no`。同一数据库的所有竞技场共享编号空间，事务回滚等情况可能产生间隔；不同服务器各自的本地 `Cache/matches.db` 不共享编号。TAB 的 `{gameId}` 使用真实数据库编号，`{gameUuid}` 显示本局 UUID；落库前编号暂显示“待分配”。BUNGEE 大厅连接同一 MySQL 后可查询竞技场子服记录；未连接共享 MySQL 的大厅不会建立一份独立的本地历史。

只有参赛玩家会写入 `bw_match_players`，纯旁观者离开不会污染对局成员。正常结束的比赛标记为 `FINISHED`；插件关闭、竞技场中止或启动恢复的遗留对局标记为 `ABORTED`，保留已成功写入的快照并可查询，但不计入完成局累计战绩。旧版仅保存的玩家累计值无法反推出逐局历史；已有逐局明细会继续使用，并按新 K/D 规则查询。管理员显式设置的 `match-statistics.enabled: false` 会保留。

### 对局统计与 VL

每次竞技场进入正式游戏状态都会生成一条对局记录。开始、结束时间和所有时间列按 `match-statistics.timezone` 写入，默认使用 `Asia/Shanghai`。进行中的玩家快照每 5 分钟上报一次，事件在发生后进入异步队列，游戏结束后再写入最终快照并把对局标记为 `FINISHED`。队列写入不会在 Bukkit 主线程上等待数据库锁。子服异常退出后再次启动时，只会把同一 `bungee-settings.server-id` 遗留的 `RUNNING` 对局标记为 `ABORTED`，不会修改其他子服的进行中对局。

`bw_match_players` 保存普通击杀、最终击杀、全部死亡（含最终死亡）、拆床、K/D、重连/掉线和胜负。K/D 始终按 `普通击杀 / max(1, 死亡)` 计算，零死亡时直接等于普通击杀数，最终击杀不计入 K/D。累计战绩只把 `FINISHED` 对局的普通击杀、最终击杀、死亡和拆床相加，再用累计普通击杀除以 `max(1, 累计死亡)`；不能把各局 K/D 相加或平均。

玩家明细同时保留本局正向 VL、负向排除证据 `evidence_adjustment` 以及下限为 0 的有效 VL `effective_vl`。`bw_match_events` 保存 `BED_BREAK`、`PLAYER_KILL`、`DISCONNECT_KILL`、`PLAYER_WIN`、`PLAYER_LOSS`、`GAME_END`、`PLAYER_LEAVE`、`RECONNECT` 和 VL 事件，事件带有对局内递增序号，重复提交不会重复插入。`bw_player_violation_totals` 同时保存不可清除的 `crime_total_vl`（累计本局正向 VL）和用于处罚判定的 `punishment_total_vl`（累计本局有效 VL）；因单局超阈值触发 `VIOLATION_EJECT` 后，最终结算事务会自动清零该玩家的 `punishment_total_vl` 和告警位图，只保留 `crime_total_vl`，并记录 `last_punished_at`。若由外部处罚系统执行处罚，可调用插件 API 的 `resetPunishmentVl(UUID)` 做同样的清零。处罚累计值严格超过 `warning-thresholds` 中的 10、20、50、100 时，异步数据库事务提交后在控制台告警，每个处罚周期每个阈值只告警一次。开局、事件和最终结算使用独立的有界关键队列，队列满载时立即告警且不阻塞主线程；最终结算会由记录器继续重试，持续满载时应提高容量或处理数据库延迟。

### 非法组队与刷人头检测

检测器只把成功的敌对伤害作为战斗证据，并在短时间窗口内组合事件；单纯靠近、一起过桥、同时攻击同一目标不会直接产生 VL。

- 不同队伍双方在 6 格内持续 8 秒、双方近期都在和敌人战斗但始终不互殴：双方各 `+2`，只作为辅助证据。
- 不同队伍的两名玩家在 5 秒窗口内反复攻击同一第三名敌人：重复达到第二、第四次时各 `+1`，最多 `+2`。
- 反复出现跨队解围/干扰追击者的伤害链：第二次候选事件各 `+2`，仍需距离和时间窗口同时满足。
- 玩家主动丢出铁、金、钻石或绿宝石后被另一队拾取，同一对玩家在 30 秒内第二次转移时各 `+5`（同一窗口只触发一次）；可用 `cross-team-item-transfer: false` 关闭。
- 同一受害者在没有对击杀者造成近期有效伤害的情况下重复送出击杀：第二次各 `+3`，第四次再各 `+1`（总计 `+4`）。
- 双方在 15 秒内正常互相造成伤害、双方互相拆床或在 120 秒内互相击杀：写入 `-3`、`-4`、`-2` 的排除证据，降低本局 `effective_vl`，但不会倒扣两个正向 VL 字段。

单局 `effective_vl` 严格超过 `match-leave-threshold`（默认 25）时，插件记录 `VIOLATION_EJECT` 后在下一 tick 调用 `IArena.removePlayer(player, false)` 将玩家送出当前对局。该检查只看当前 `MatchRecord`，且会在游戏结束事件发出后停止；已经掉线或不在竞技场的玩家只保留审计记录，不会异步操作 Bukkit 世界。被标记处罚的玩家会在最终结算事务中自动清零累计处罚值，犯罪记录不会清除；外部处罚系统仍可按需调用 `resetPunishmentVl(UUID)`。

常用汇总查询示例：

```sql
SELECT player_uuid, player_name, matches_played, wins, losses,
       normal_kills, final_kills, deaths, beds_destroyed, kd_ratio,
       effective_vl, crime_total_vl, punishment_total_vl
FROM bw_player_match_summary
ORDER BY wins DESC, kd_ratio DESC;
```

游戏开始时实际只有一名玩家的队伍不会启用队内私聊：该玩家直接使用 `format-chat-global` 格式向当前竞技场的存活玩家发言，不需要 `/shout` 权限，死亡旁观者不会收到这类对局聊天。判断使用开局人数快照；队友在开局后掉线、重连或被淘汰不会中途切换聊天频道。开局至少两人的队伍继续使用 `format-chat-team` 队内聊天，并可通过 `/shout`、`/hh`、`/h` 或六种前缀主动发到公屏；旁观者聊天仅发送给同场旁观者。7.0.0 起喊话没有插件冷却；主配置架构 28 会删除旧 `shout-cmd-cooldown`，管理员无需再维护该值。

3.0.0 的主配置架构 25 会把仍使用上一版默认值 `speed-multiplier: 11`、`sneak-speed-multiplier: 1.25` 的配置更新为 16 和 1.5；更早仍为默认 10 的配置会直接迁移到 16。管理员设置的其他速度不会被覆盖。5.1.0 的主配置架构 31 只补入缺失的 Paper 运行配置，不会改写已有火球参数。8.0.0 的主配置架构 29 新增 `sneak-acceleration-multiplier: 2.0`，并只把仍为旧内置值的 `sneak-recoil: 0.05`、`cooldown: 0.5` 分别迁移到 0.10 和 0.4；管理员自定义值保持不变。10.0.0 的主配置架构 30 只把仍为精确旧默认值的 `speed-multiplier: 16`、`sneak-speed-multiplier: 1.5` 调整为 15 和 1.6，已自定义的任一项不会覆盖。Paper 1.21.11/26.2 的火球加速度现在直接调用 `Fireball#setAcceleration(Vector)`，不再访问 NMS 字段或保留低版本反射分支；射程任务随单个火球实体运行，不做全服扫描，也不为远距离飞行强制加载区块。5.3.0 的主配置架构 34 只把仍为默认值 `knockback.horizontal: 1.15` 提高为 1.35，并把仍为默认值 `tnt-jump-settings.damage-others: 10` 降为 6，同时补入 `tnt-jump-settings.knockback-multiplier: 0.6`；管理员自定义值不会被覆盖。配置升级前会生成旧文件备份，修改火球参数后需要完整重启服务器。

竞技场配置架构 11 会把 Paper 1.21.11 已废弃的 `doFireTick` 自动删除，并写入 `fireSpreadRadiusAroundPlayer:0`。插件还会在运行时拦截 `SPREAD` 原因的点燃事件，防止其他插件或旧世界规则重新开启火势蔓延。火焰与灵魂火不会再被爆炸保护射线当作地图实体方块，因此着火表面的玩家放置方块仍可被火球正常炸毁。

TAB 相关常用项：

- `scoreboard-settings.tab-header-footer.enable`：显示 TAB 顶部和底部内容，默认开启，且不依赖右侧大厅计分板。
- `scoreboard-settings.tab-header-footer.refresh-interval`：TAB 页首页尾动态内容刷新周期，默认 `20` tick。游戏时间复用此任务和内容缓存；小于 1 时按现有配置语义关闭动态刷新。
- `scoreboard-settings.tab-header-footer.lobby-header`：仅配置大厅 TAB 顶部文字。空列表沿用语言文件中的默认内容；非空时逐行填写，系统仍会自动保留内置宽度行。支持 `&` 颜色代码和 `{serverIp}`、`{on}` 等占位符，大厅页尾及竞技场各状态不会被覆盖。
- `scoreboard-settings.player-list.format-lobby-list`：显示大厅玩家前后缀，默认开启。
- `scoreboard-settings.player-list.names-refresh-interval`：TAB 动态文字刷新周期，默认 `1200` tick（60 秒），小于 1 时关闭周期刷新。玩家行平时由开局、登录、重连、重生和切换可见性等事件即时同步；周期刷新按当前队伍、重生状态重放完整显示名，并检查 scoreboard 是否发生漂移。行显示名或 Team 元数据未变化时不重复写入。关闭周期刷新不会关闭事件同步。
- 游戏进行中的存活玩家及结算阶段仍有原队伍归属的参赛玩家，内置行前缀默认使用 `&7[{teamColor}{teamName}&7] `，显示为 `[{队伍名字}] {玩家名字}`；方括号为灰色，队名与玩家名沿用队伍颜色。管理员仍可使用 `{teamName}`、`{teamLetter}` 和 `{teamColor}` 自定义前后缀。插件通过 Paper scoreboard team 维护队伍颜色、碰撞和头顶名牌，再按查看者发送完整 PlayerInfo 显示名。加入、重连、重新可见和重生时会重新发送当前行。
- 等待重生的玩家仍属于参赛玩家，保留队伍色并以斜体显示，复活后恢复普通字体。最终淘汰与开局后加入的旁观者统一排在所有参赛玩家之后；游戏中的玩家不显示同场旁观者，旁观者可看到完整对局名单及旁观行。旁观行按对应语言模板显示，不修改真实游戏模式，也不会向玩家本人发送伪造的观察者模式。离场时释放该查看者的隐藏状态。即使关闭侧边栏、页首页尾和完整 TAB 格式，核心队伍色、排序和旁观者隐藏仍生效。等待和倒计时阶段尚未正式分队；正式开局后显示实际颜色。队伍按红→黄→绿→深绿→青→蓝→粉排列，非光谱色白→灰→深灰置后；同队存活与等待重生玩家按完整玩家名字典序排列。旧 `teammate-color` 配置会自动删除；`format-tab.playing.game-time` 保留管理员已有文本。
- scoreboard Team 独立负责头顶名牌颜色和碰撞；进行中的存活玩家共享真实队伍并使用 `FOR_OTHER_TEAMS`，只关闭同队玩家之间的推动，敌队保持碰撞。观战和死亡等待的实体级无碰撞由 Paper API 单独管理。
- 存活、已淘汰和纯旁观三种游戏中页首都会插入本局已进行时间。`{gameTime}` 显示 `MM:SS`，超过一小时显示 `HH:MM:SS`；`{time}` 仍表示下一事件倒计时。等待、开局倒计时和重置阶段不会显示继续增长的本局时间。竞技场 TAB 页首还会在地图信息后显示 `{gameId}` 对局编号；将语言文件的 `format-tab.game-id` 设为空字符串可隐藏。将语言文件的 `format-tab.playing.game-time` 设为空字符串可隐藏该行；若自定义页首已经直接包含 `{gameTime}`，插件不会重复插入。
- `scoreboard-settings.health.display-in-tab`：在 TAB 中额外显示生命值数字；默认关闭，避免与原版网络延迟图标混淆。头顶生命值由 `scoreboard-settings.health.enable` 单独控制；旁观玩家在两处都不显示生命值。
- 正式开局时就为空的队伍不会出现在游戏中或重置阶段的侧边栏；参战后被淘汰的队伍仍保留。这同时适用于通用 `{team}` 行和显式 `{Team<队伍>...}` 占位符行。

大厅和竞技场全部状态统一保证至少 128 空格宽度；旧语言文件中 104 空格的 BedWars1058 页首会在发送时自动拓宽，自定义的更宽页首保持不变。除宽度行外，原有行数、颜色代码、占位符和动画帧不变。

竞技场配置中的 `shop-protection` 和 `upgrades-protection` 控制 NPC 周围保护半径，运行时最小为 1 格。保护按方块坐标对称计算，并覆盖 NPC 脚部与头部周围一格。

`countdowns.game-restart` 默认迁移为 60 秒，聊天栏仅在 `60、30、15、10、5、4、3、2、1、0` 秒时提示。到 0 秒后先把玩家安全送回大厅；只有竞技场世界已经没有玩家且所有异步传送完成，插件才会卸载并恢复地图。传送失败会保持世界加载并重试，不会踢出玩家。

## Arenas/<竞技场>.yml

每张地图一份。主要节点：

- `group`、`display-name`、`minPlayers`、`maxInTeam`。每张竞技场只能属于一个组；该值同时用于匹配、生成器、开局物品、升级菜单和计分板等组专属配置。2.13.x 的 `groups` 列表会保留第一项并自动迁移回单值。
- `allowSpectate`、`worldBorder`、`y-kill-height`、最大建造高度。新建竞技场的 `y-kill-height` 默认是 `-70`，已有地图保留其已保存的数值。
- 出生、商店、升级、生成器保护半径。
- `island-radius`：自动找床、治疗池和陷阱检测范围。
- `game-rules`：`规则:值` 列表。Paper 1.21.11 使用 `advance_time`、`advance_weather` 和 `spawn_mobs`；旧配置中的 `doDaylightCycle`、`doWeatherCycle`、`doMobSpawning`、`announceAdvancements`、`doInsomnia` 和 `doImmediateRespawn` 会在运行时显式映射到现代注册键，无需手动改写。竞技场初始化时设置正午 `6000 tick`、晴天、关闭昼夜与天气推进，并禁用生物自然生成和随机方块刻。后续 `/time`、睡眠、插件跳时、`/gamerule`、下雨和雷暴均由事件守卫改回固定状态，不进行周期扫描。树叶腐烂、作物生长、草地/蘑菇蔓延、结冰融化等竞技场自然方块变化仍会被阻止。正午与晴天保证普通主世界露天的最高自然天光，不等于洞穴、下界或末地 Fullbright。
- `waiting`、`spectator-loc`：等待和观战标点。
- `Team`：所有队伍的颜色、出生点、床、NPC 和岛屿生成器。
- `generator.Diamond`、`generator.Emerald`：中央生成器列表。

分组示例：

```yaml
group: Solo
```

玩家通过 `/bw join Solo` 或对应竞技场选择器匹配这张地图。`/bw arenaGroup set <竞技场> <分组>` 会覆盖原分组，不再支持追加到多个组。

`Team.<队伍>.Color` 的青色填写 `CYAN`。2.10.11 起旧值 `AQUA` 会自动迁移为 `CYAN`，对应床、羊毛、玻璃和陶瓦均使用原版 `CYAN_*` 材质。

2.10.26 起 `/bw autoCreateTeams` 直接使用羊毛的完整 `Material` 做精确映射，不使用模糊名称匹配。亮绿/深绿分别是 `LIME_WOOL → GREEN`、`GREEN_WOOL → DARK_GREEN`；亮灰/深灰分别是 `LIGHT_GRAY_WOOL → GRAY`、`GRAY_WOOL → DARK_GRAY`。

2.10.43 起不再提供钻石/绿宝石生成点的结构扫描。`generator.Diamond`、`generator.Emerald` 仅由 `/bw addGenerator` 和 `/bw removeGenerator` 明确维护，`/bw save` 不会隐式扫描或改写；队伍床位的自动识别保持不变。

队伍名称直接取 `Team.<队伍>` 的节点名并保持原大小写，不经过语言翻译。旧语言文件中可能存在的 `team-name-<竞技场>-<队伍>` 不再参与显示。

坐标由插件生成，建议使用设置命令，不要手写。方向字段与坐标分开，避免破坏方块中心格式。所有玩家、NPC 和大厅传送朝向的 yaw 自动取最近的 90 度倍数，pitch 固定为 `0.0`。商店和升级村民关闭 AI、感知与重力，并逐 tick 只在发生偏移时恢复位置、身体和视线方向，不会自行移动或扭头。

`minPlayers` 是整个竞技场进入倒计时所需的最低人数，可用 `/bw setMinPlayers <人数>` 修改，最小为 2；`maxInTeam` 是每支队伍容量，可用 `/bw setMaxInTeam` 修改。两者互不覆盖。正常匹配在总人数达到 `minPlayers`、不超过地图总容量，且能形成至少两支非空队伍时开始；每支队伍只需不超过 `maxInTeam`，不再要求达到额外的每队下限。例如 `minPlayers: 2`、`maxInTeam: 4` 时，两名未组队玩家会分到两队并开局；同一个两人小队则等待至少一名对手。分配器实时根据当前固定小队、地图队伍数和容量计算，以最少的非空队伍为目标并优先填满已启用队伍；固定小队会保持成员关系，其他玩家按自动均衡规则分配。不使用配置组合表或历史状态表。正常大小的小队保持完整，只有超过 `maxInTeam` 的外部大队伍才会拆分。倒计时结束及 `TeamAssignEvent` 后会再次校验最低人数、至少两队和容量；`/bw start debug` 仍可供 OP 单队测试。等待人数达到 `maxPlayers` 时，若开局倒计时超过 5 秒，插件会直接压缩到固定 5 秒后继续开始；该规则不提供配置项，旧 `countdowns.game-start-shortened` 会在主配置架构 31 迁移时删除。

## generators.yml

文件位置：`plugins/SimpMC-BedWars/generators.yml`。它控制铁、金、钻石、绿宝石的：

- `delay`：生成间隔秒数。
- `amount`：每次生成数量。
- `spawn-limit`：生成点附近同类地面积压达到该总数后暂停生成，拾取后自动恢复；设为 `0` 表示不限制。
- `start`：II/III 级启用时间。
- `stack-items`：合并掉落物以减少实体。

当前默认队伍资源配置为：

```yaml
Default:
  iron:
    delay: 1
    amount: 2
    spawn-limit: 32
  gold:
    delay: 4
    amount: 2
    spawn-limit: 7
```

`delay` 就是真实刷新间隔秒数，越小刷新越快；0 或负数会被安全限制为 1 秒。`amount` 越大，每次产出越多。`spawn-limit` 是生成点附近同类地面资源的总数上限，达到后暂停生成，拾取后自动恢复，0 表示不限制。修改后需要完整重启服务器。建议把根节点的 `stack-items` 设为 `true`；插件会在生成时直接按材质最大堆叠数合并，同样产量只创建最少数量的地面物品实体。

复制 `Default` 并改为竞技场 `group` 名称，可为不同模式设置独立速度。如果已经存在与竞技场组同名的配置节，游戏会优先读取该节，而不是 `Default`。

旧版仍使用内置默认值时会自动把铁间隔从 2 秒迁移为 1 秒、金间隔从 6 秒迁移为 4 秒；自定义值及自定义分组不会被覆盖。

5.8.0 恢复 BW1058 原仓库的空队伍停产语义：竞技场配置中的 `disable-generator-for-empty-teams: true` 会让“无成员且床已摧毁”的队伍岛屿停止生成资源，包括开局没有分配到玩家的队伍和全队最终淘汰的队伍。队员外出、等待重生以及仍保留床的暂时掉线不会触发；中央钻石和绿宝石生成器不受队伍是否为空影响。此判断复用已有生成任务，不扫描岛屿附近玩家。

5.9.1 恢复 BW1058 的 `spawn-limit` 地面积压停产：生成点附近同类地面资源总数达到 `spawn-limit` 时暂停生成，拾取后自动恢复；设为 `0` 表示不限制。该判断复用每秒生成任务，只查询生成点附近同类掉落物，不增加周期扫描。铁、金、钻石、绿宝石以及团队熔炉升级生成的资源都按此规则工作。

竞技场配置架构 23 对缺失的空队伍开关补入 `true`，保留已有 `true/false`。旧地图若把该开关设为 `false`，请改为 `true` 后完整重启以启用空队伍停产。生成器配置架构 6 保留已有速度、数量、上限值和自定义分组，已完成旧速度迁移的配置不会被再次改写。

## shop.yml

文件位置：`plugins/SimpMC-BedWars/shop.yml`。商品价格路径为：

```text
<分类>.category-content.<商品>.content-tiers.<等级>.tier-settings.cost
```

同级的 `currency` 决定货币。以下示例把 16 个羊毛设为 4 铁：

```yaml
blocks-category:
  category-content:
    wool:
      content-settings:
        content-slot: 19
        is-permanent: false
      content-tiers:
        tier1:
          tier-item:
            material: WHITE_WOOL
            amount: 16
          tier-settings:
            cost: 4
            currency: iron
```

例如 TNT 默认路径是 `utility-category.category-content.tnt.content-tiers.tier1.tier-settings.cost`。修改 `cost` 后完整重启服务器。

回城卷轴的商品 ID 是 `recall-scroll`，默认价格为 3 个钻石，价格路径为 `utility-category.category-content.recall-scroll.content-tiers.tier1.tier-settings.cost`。购买物品中的 `bedwars-item: recall-scroll` 是核心行为标识；可以修改价格、槽位、显示材质和名称，但不要删除或改写该标识。

袖珍弹出塔的商品 ID 是 `tower`，默认价格路径为 `utility-category.category-content.tower.content-tiers.tier1.tier-settings.cost`。它的显示名称和说明位于语言文件 `shop-items-messages.utility-category.content-item-tower-name` 与 `shop-items-messages.utility-category.content-item-tower-lore`。2.10.40 会自动删除错误的旧 `Compact Pop-up Tower` 语言节点，并把其中的文本迁移到 `tower`；系统生成的 `Name not set`/`Lore not set` 会被正确默认值替换，管理员已经写在正确路径的自定义文本保持不变。

商店永久护甲固定只发放护腿和靴子。玩家开局时仍穿戴完整的本队颜色皮革套装，购买链甲、铁甲或钻石甲后，队伍色皮革头盔与胸甲会继续保留。旧 `config.yml` 中的 `shop-settings.sell-full-armor` 会自动删除；`shop.yml` 升级到架构 5 时会清理所有护甲分类中的头盔、胸甲和鞘翅商品，价格与下半身自定义商品保持不变。

货币支持 `iron`、`gold`、`diamond`、`emerald`；同时安装 Vault 和实际经济服务提供者后，可按现有格式使用经济货币。只安装 Vault 不会创建余额。Material、附魔和药水名称必须适用于 Paper 1.21.11。

团队升级价格不在 `shop.yml`，而在 `plugins/SimpMC-BedWars/upgrades2.yml` 的 `<升级>.tier-<等级>.cost`，例如 `upgrade-forge.tier-1.cost`。

## upgrades2.yml

升级的 `receive` 支持以下动作：

- `enchant-item: 附魔,等级,目标类型`
- `player-effect: 效果,等级,持续秒数,team|base|enemy`
- `generator-edit: 资源,延迟,数量,上限`
- `remove-effect: 效果,enemy`
- `dragon: 数量`

每个 tier 还需要 `cost`、`currency` 和 `display-item`。陷阱按队列顺序触发，队列上限和价格增量位于 `default-upgrades-settings`。

`upgrade-swords.tier-1` 到 `tier-4` 对应锋利 I–IV，默认价格为 4、8、16、32 钻石；`upgrade-armor.tier-1` 到 `tier-4` 对应保护 I–IV，默认价格为 2、4、10、24 钻石。`upgrades2.yml` 架构 11 会把仍为架构 10 内置整组值 `2、4、8、16` 的锋利价格迁移到新价格；任一级被自定义时，锋利四级价格全部保持不变。保护价格仍按自身的旧架构独立迁移。

## sounds.yml

每个事件包含：

```yaml
event-name:
  sound: ENTITY_PLAYER_LEVELUP
  volume: 1.0
  pitch: 1.0
```

声音名称使用 Bukkit Sound。无效声音会使用安全回退并在排查时显示日志。

## levels.yml

- `levels`：等级名称和升级经验，支持 `5-10` 范围及 `others` 回退。
- `xp-rewards`：分钟、队友、胜利、破床、普通击杀、最终击杀经验。
- `progress-bar`：进度字符、已完成/未完成颜色和格式。

## rewards.yml

Vault 金币奖励。没有 Vault 或没有向 Vault 注册的经济服务提供者时，文件仍会生成，但奖励不会生效。服务可用状态以启动日志中的 `已接入 Vault 经济服务` 为准。

## signs.yml

- `format`：告示牌四行文本。
- 状态材质：waiting、starting、playing、restarting。
- `locations`：插件自动维护的告示牌坐标，不建议手动编辑。

## Languages/messages_zh_cn.yml

玩家可见文本。注意：

- `&` 后接颜色代码。
- `{player}`、`{message}`、`{TeamName}` 等占位符不能删除或改名。
- 聊天运行时统一为 `&f> &7消息`。
- `.bak` 是自动迁移备份，不会加载为语言。
- 语言配置架构 2 会迁移袖珍弹出塔的旧商品文本键。当前公开版本 5.1.2 的简体中文内置文件使用架构 19；旧版本留下的其他语言文件和自定义文件会保留在磁盘上，但本版本不再加载或暴露它们。
- 语言架构 21 只会把完全等于已知历史内置值的 TAB 玩家行前缀迁移为 `&7[{teamColor}{teamName}&7] `，包括进行中的存活玩家、结算时的胜方存活玩家、胜方淘汰玩家和败方玩家四类队伍行；已知的历史淘汰后缀也会迁移到当前内置值。只要某一列表与内置旧值不完全相同，就视为管理员自定义并在磁盘上原样保留；运行时会正常解析 `{teamName}`、`{teamLetter}` 和 `{teamColor}`。`format-tab.playing.game-time`、自定义聊天、未知键和其他自定义模板不会被覆盖。迁移前仍会生成 `.bak` 备份。

## 配置修改原则

1. 修改前备份。
2. 不要使用 Tab 缩进，只用空格。
3. Material、Sound、PotionEffect 和 Enchantment 使用 Paper 1.21.11 名称。
4. 关键配置修改后完整重启，不使用 `/reload`。
5. 启动后先检查控制台，再进行一局完整测试。
