package com.andrei1058.bedwars.configuration;

import com.andrei1058.bedwars.api.configuration.ConfigManager;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Centralized Chinese documentation for every administrator-facing YAML file.
 */
public final class ChineseConfigDocumentation {

    private ChineseConfigDocumentation() {
    }

    public static void main(ConfigManager config) {
        comment(config, "serverType", "服务器模式：MULTIARENA（多竞技场）、SHARED（共享服务器）或 BUNGEE（代理网络）。", "MULTIARENA/BUNGEE 会把实例内全部世界固定为正午和晴天；SHARED 只处理竞技场、设置世界和配置大厅。", "修改后必须完整重启服务器。");
        comment(config, "language", "服务器语言固定为 zh_cn（简体中文）；旧语言值会自动迁移。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_DISABLED_LANGUAGES, "历史兼容字段；当前版本只提供简体中文，不再加载其他语言。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_ARENA_GROUPS,
                "可用于匹配和竞技场选择器的全局分组名称；Default 是内置组，无需填写。",
                "每张竞技场地图只能引用其中一个分组。");
        comment(config, "storeLink", "服务器商店或官方网站地址，可用于消息占位符。");
        comment(config, "lobbyServer",
                "BungeeCord/Velocity 代理 [servers] 中的主大厅服务器名称，不是 IP、端口或 MotD。",
                "名称必须与代理配置一致；Velocity 需在 velocity.toml 的 [advanced] 中设置 bungee-plugin-message-channel = true。",
                "仅缺失时补入默认值 hub；自动升级和排序会保留已有值，例如 login。",
                "MULTIARENA 和 BUNGEE 模式下的主大厅返回物品都会静默直接连接此服务器。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_ENABLE_HALLOWEEN, "是否启用万圣节季节效果。");
        comment(config, ConfigPath.GENERAL_CHAT_GLOBAL, "聊天设置：global 控制不同竞技场是否互通，format 控制是否使用插件聊天格式。");
        comment(config, "debug", "调试日志开关；排查问题时临时开启，正常运行建议关闭。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_MARK_LEAVE_AS_ABANDON, "玩家主动离开进行中的游戏时，是否记为中途退出。");
        comment(config, ConfigPath.GENERAL_ENABLE_PARTY_CMD, "组队系统设置：是否启用命令、是否允许组队以及外部 Parties 的最低等级。");
        comment(config, ConfigPath.SB_CONFIG_SIDEBAR_USE_LOBBY_SIDEBAR, "计分板、TAB 玩家列表、血量显示及刷新周期设置。", "TAB 页首和页尾按游戏状态读取玩家语言文件。", "刷新周期单位为 tick，20 tick 约等于 1 秒。");
        comment(config, ConfigPath.SB_CONFIG_SIDEBAR_LIST_REFRESH,
                "TAB 动态文字刷新周期，默认 1200 tick（60 秒）；小于 1 时关闭。",
                "周期刷新只同步发生变化的队伍显示属性，不会重复写入静态内容。");
        comment(config, ConfigPath.SB_CONFIG_TAB_LOBBY_HEADER, "仅自定义大厅 TAB 顶部文字；空列表沿用语言文件中的加宽样式。", "自定义文字会自动保留内置宽度行；支持 & 颜色代码及 {serverIp}、{on} 等占位符，不会改变大厅页尾或其他游戏状态。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_REJOIN_TIME, "掉线玩家允许重连的时间，单位为秒；超时后直接视为离开。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_MODE_GAMES_BEFORE_RESTART, "BUNGEE/自动扩容相关设置：重启场次、重启命令、节点 ID、超时及大厅地址。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_SERVER_ID,
                "BUNGEE 子服在代理和对局数据库中的唯一节点 ID；每个子服必须使用不同值，不能沿用默认 bw1。",
                "该 ID 也用于启动恢复，只会处理本节点上次异常退出的 RUNNING 对局。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_BWP_TIME_OUT,
                "ARENA 子服等待代理登录预加载条目的时间，单位为毫秒；默认 23000。",
                "实际最短时间会覆盖大厅预加载确认超时，并额外保留 15 秒代理切服余量；旧配置中的较小值会自动抬高。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_NODE_ROLE,
                "BUNGEE 节点角色：ARENA 负责加载地图、运行对局和上报状态；LOBBY 不加载竞技场，只维护远程目录并调度玩家。",
                "旧版 BUNGEE 配置默认使用 ARENA；拆分部署时大厅服必须设置为 LOBBY。修改后需要完整重启服务器。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_PROXY_SERVER,
                "本节点在 BungeeCord/Velocity [servers] 中的服务器键名，用于大厅调度后的 Connect。",
                "留空时使用 server-id；ARENA 子服必须与代理中的后端键名一致，LOBBY 节点不参与竞技场路由。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_ARENA_TEMPLATE,
                "ARENA 子服负责的地图配置文件名，不含 .yml；同一子服只为此地图自动复制副本。",
                "留空时保留旧版行为并加载 Arenas 目录中的全部地图；新的拆分部署建议每个子服明确填写一个地图名。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_LOBBY_LISTEN_HOST,
                "LOBBY 节点监听竞技场状态套接字的绑定地址；建议只绑定内网地址。仅 LOBBY 角色使用。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_LOBBY_LISTEN_PORT,
                "LOBBY 节点监听竞技场状态套接字的 TCP 端口；每个大厅服端口必须可被 ARENA 子服访问。仅 LOBBY 角色使用。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_SOCKET_SECRET,
                "竞技场与大厅套接字共享密钥；非空时双方 HELLO 必须完全一致。",
                "建议设置随机长字符串并用防火墙限制监听端口；留空仅用于受信任的旧网络兼容。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_NODE_TIMEOUT_SECONDS,
                "大厅判定竞技场节点状态过期的时间，单位为秒；过期节点不会被新玩家调度。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_DISPATCH_TIMEOUT_SECONDS,
                "大厅等待 ARENA 子服确认玩家预加载的时间，单位为秒；超时会释放预约并提示重试，不阻塞主线程。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_STATUS_HEARTBEAT_SECONDS,
                "ARENA 子服向大厅发送全量状态心跳的间隔，单位为秒；状态变化仍会即时上报。仅用于断线恢复。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_LOBBY_SERVERS, "大厅套接字地址列表，格式为 主机:端口。", "协议使用共享密钥认证但不加密，只能填写受信任的内网地址，并使用防火墙禁止公网访问。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_START_COUNTDOWN_REGULAR, "游戏各阶段倒计时，单位为秒。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_RESTART,
                "游戏结束后的竞技场重置倒计时，单位为秒。",
                "倒计时结束后先安全返回大厅；确认世界无人后才卸载，传送失败会重试而不是踢出玩家。");
        comment(config, ConfigPath.GENERAL_CONFIG_PLACEHOLDERS_REPLACEMENTS_SERVER_IP, "内置占位符显示的服务器地址和品牌文本。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_HUNGER_WAITING, "是否允许等待阶段和游戏阶段消耗饥饿值。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_ALLOW_FIRE_EXTINGUISH, "玩家是否可以扑灭竞技场中的火焰。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_HEAL_POOL_ENABLE, "队伍基地治疗池设置。");
        comment(config, ConfigPath.GENERAL_TNT_JUMP_BARYCENTER_IN_Y, "TNT 跳跃力度、伤害与重心修正参数。");
        comment(config, ConfigPath.GENERAL_TNT_JUMP_DAMAGE_OTHERS, "玩家 TNT 对敌方玩家造成的固定伤害；5.3.0 默认值由 10 降为 6，-1 表示沿用原版伤害。");
        comment(config, ConfigPath.GENERAL_TNT_KNOCKBACK_MULTIPLIER,
                "玩家 TNT 对对局内玩家的原版爆炸击退倍率；1.0 为原版击退，默认 0.6，范围 0 到 3。",
                "该倍率不改变 TNT 跳跃自身的力度公式，只削弱爆炸本身附加的击退。");
        comment(config, ConfigPath.GENERAL_TNT_PROTECTION_END_STONE_BLAST, "TNT 对末地石、玻璃等方块的爆炸抗性参数。");
        comment(config, ConfigPath.GENERAL_TNT_AUTO_IGNITE, "TNT 放置后是否自动点燃，以及引信持续 tick 数。");
        comment(config, ConfigPath.GENERAL_FIREBALL_EXPLOSION_SIZE,
                "火球方块爆炸强度，以及伤害和击退生效的三维球形半径；2.10.20 默认值略微降低为 3.25，插件限制最大 16 格。",
                "玩家判定使用到火球位置的欧氏距离；过大的值会自动限幅，避免一次爆炸扫描过多实体。");
        comment(config, ConfigPath.GENERAL_FIREBALL_SPEED_MULTIPLIER,
                "普通投掷的火球初速度倍率；默认 15，对应 1.5 格/tick。潜行投掷默认初速度更高。");
        comment(config, ConfigPath.GENERAL_FIREBALL_SNEAK_SPEED_MULTIPLIER,
                "按住潜行键投掷的额外初速度倍率；默认 1.6，对应 2.4 格/tick。", "插件同时检查 Paper 当前输入与潜行姿态，确保潜行加速生效。");
        comment(config, ConfigPath.GENERAL_FIREBALL_SNEAK_ACCELERATION_MULTIPLIER,
                "潜行火球相对普通火球的持续加速度倍率；默认 2.0，对应每 tick 0.2。", "该参数不会覆盖潜行火球已经获得的 2.4 格/tick 初速度。");
        comment(config, ConfigPath.GENERAL_FIREBALL_FLIGHT_RANGE_MIN,
                "每颗火球随机最大射程的下限，单位为格；默认 200。", "射程按相邻 tick 的实际三维路径累计，不按存活时间估算。");
        comment(config, ConfigPath.GENERAL_FIREBALL_FLIGHT_RANGE_MAX,
                "每颗火球随机最大射程的上限，单位为格；默认 300。", "上下限写反时会自动交换；插件不会为了远距离飞行强制加载区块。");
        comment(config, ConfigPath.GENERAL_FIREBALL_SNEAK_RECOIL, "潜行投掷时给予发射者的三维后坐速度；默认 0.10，插件硬限制最大 0.20。", "方向始终与火球发射速度完全相反，包括火球向上或向下飞行时的垂直分量。");
        comment(config, ConfigPath.GENERAL_FIREBALL_MAKE_FIRE, "火球爆炸后是否在命中处生成火焰；竞技场始终禁止火势向周围蔓延。");
        comment(config, ConfigPath.GENERAL_FIREBALL_KNOCKBACK_HORIZONTAL,
                "火球水平击退强度；5.3.0 默认值提高为 1.35，插件限制最大 8。",
                "水平击退沿水平面单独归一化，不会因玩家与爆炸中心的高度差而减弱。");
        comment(config, ConfigPath.GENERAL_FIREBALL_KNOCKBACK_VERTICAL, "火球垂直击退强度；2.10.20 默认值略微降低为 0.75，插件限制最大 8。");
        comment(config, ConfigPath.GENERAL_FIREBALL_COOLDOWN, "连续使用火球的冷却时间，单位为秒；默认 0.4，持续射速约每秒 2.5 发。", "按发射时刻计算，一个 1 秒窗口内通常可以发射 2 至 3 个火球。");
        comment(config, ConfigPath.GENERAL_FIREBALL_DAMAGE_SELF, "火球对发射者造成的伤害。");
        comment(config, ConfigPath.GENERAL_FIREBALL_DAMAGE_ENEMY, "火球对敌人造成的伤害；2.10.20 默认值略微降低为 3.5。");
        comment(config, ConfigPath.GENERAL_FIREBALL_DAMAGE_TEAMMATES, "历史兼容字段；当前版本火球不会对队友造成伤害，管理员可保留原值。");
        comment(config, "database.enable", "数据存储设置：关闭时使用 SQLite，开启时连接 MySQL。", "修改连接信息后必须完整重启，切勿公开数据库密码。");
        comment(config, "database.pass", "MySQL 专用账户密码；不要沿用示例值，也不要提交到公开仓库。");
        comment(config, "database.ssl", "MySQL 连接是否启用 TLS；远程数据库建议开启并限制允许连接的来源地址。");
        comment(config, ConfigPath.MATCH_STATISTICS_ENABLED,
                "是否记录每场对局及其玩家战绩；默认开启，未启用 MySQL 时保存到本地 Cache/matches.db。",
                "跨服请使用同一 MySQL；已启用 MySQL 但连接失败时不会切换本地编号。旧 global_stats 统计保持兼容。");
        comment(config, ConfigPath.MATCH_STATISTICS_TIMEZONE,
                "竞技场统计时区，默认 Asia/Shanghai；所有子服建议保持一致。",
                "时间列按该时区生成，数据库同时保存时区名称；无效值会回退为 Asia/Shanghai。");
        comment(config, ConfigPath.MATCH_STATISTICS_REPORT_INTERVAL_SECONDS,
                "进行中对局的异步上报间隔，单位为秒；默认 300（5 分钟）。");
        comment(config, ConfigPath.MATCH_STATISTICS_QUEUE_CAPACITY,
                "异步统计写入队列容量；队列满时不会阻塞主线程，最终结算会继续重试。");
        comment(config, ConfigPath.MATCH_STATISTICS_RETRY_DELAY_SECONDS,
                "对局数据库写入失败后的重试间隔，单位为秒。");
        comment(config, ConfigPath.MATCH_STATISTICS_FINISH_GRACE_TICKS,
                "收到游戏结束事件后等待的 tick 数，再写入最终结算；用于接收同一 tick 内的掉线击杀。");
        comment(config, ConfigPath.MATCH_STATISTICS_VIOLATIONS_ENABLED,
                "是否启用非法组队和刷人头检测及 VL 保存。",
                "关闭只停止 VL 累加，不影响普通击杀、最终击杀、拆床和死亡统计。");
        comment(config, ConfigPath.MATCH_STATISTICS_VIOLATIONS_WARNING_THRESHOLDS,
                "处罚依据累计 VL 严格超过这些值时在控制台告警；默认 10、20、50、100。",
                "每个阈值只在累计值首次跨过时打印一次；处罚重置后可以再次告警。");
        comment(config, ConfigPath.MATCH_STATISTICS_VIOLATIONS_MATCH_LEAVE_THRESHOLD,
                "单局有效 VL 严格超过此值时，下一 tick 将玩家移出当前对局；默认 25。");
        comment(config, ConfigPath.MATCH_STATISTICS_VIOLATIONS_CROSS_TEAM_ITEM_TRANSFER,
                "是否把不同队伍之间重复掉落/拾取铁、金、钻石、绿宝石作为强证据；默认开启。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_PERFORMANCE_ROTATE_GEN, "性能设置；Paper 优化通常建议保持开启。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_DISABLE_CRAFTING, "竞技场内合成台、附魔台、熔炉、酿造台和铁砧的禁用设置。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH,
                "MULTIARENA 大厅物品：默认包含历史战绩、竞技场选择器和返回主大厅红床。",
                "每项可配置命令、材质、数据值、附魔外观和背包槽位；已有节点只补缺失字段，删除整个节点后不会重建。",
                "4.0.8 会从架构 15 删除前的最后快照，或架构 15–17 中重新配置过的快照，恢复曾被旧版本误删且后来仍为默认值的自定义 leave 节点。",
                "leave 节点固定标记为代理大厅；等待区和旁观区的 leave 节点固定返回本服 BedWars 大厅。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_PATH, "竞技场等待阶段物品；格式与大厅物品相同。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_PATH, "观战状态物品；格式与大厅物品相同。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_ARENA_SELECTOR_SETTINGS_SIZE,
                "竞技场选择菜单设置；默认 54 格、每页 45 张地图，更多地图自动分页。",
                "菜单大小必须是 9 的倍数，槽位使用英文逗号分隔；底行左、中、右保留给翻页控件。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_STATS_GUI_SIZE, "历史战绩菜单大小和每个统计项的材质、槽位。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_DEFAULT_ITEMS, "按竞技场分组配置开局默认物品，填写 Bukkit Material 名称。");
        comment(config, ConfigPath.CENERAL_CONFIGURATION_ALLOWED_COMMANDS, "游戏中允许玩家使用的命令名称，不要填写开头的斜杠。");
        comment(config, ConfigPath.GENERAL_CONFIGURATION_ENABLE_GEN_SPLIT, "是否允许同队玩家共享生成器掉落物。");
        comment(config, ConfigPath.LOBBY_VOID_TELEPORT_ENABLED, "大厅虚空传送开关和触发高度。");
        comment(config, ConfigPath.GENERAL_GAME_END_SHOW_ELIMINATED, "游戏结束时淘汰玩家、传送、聊天最佳数据和计分板最佳数据的显示设置。");
    }

    public static void arena(ConfigManager config) {
        comment(config, "group", "竞技场唯一的匹配分组，也用于生成器、开局物品、升级菜单和计分板等组专属配置。", "旧版 groups 列表会保留第一项并自动迁移为单值。");
        comment(config, ConfigPath.ARENA_DISPLAY_NAME, "玩家可见名称；留空时使用竞技场世界名。");
        comment(config, "minPlayers", "开始正常匹配倒计时所需的全场最低玩家数，必须至少为 2；可使用 /bw setMinPlayers 修改。", "分队时还会检查 maxInTeam，并确保至少产生两个非空队伍。");
        comment(config, "maxInTeam", "每支队伍可容纳的最大玩家数；所有设置模式均可使用 /bw setMaxInTeam 修改。", "修改队伍容量不会覆盖全场最低开局人数 minPlayers。");
        comment(config, "allowSpectate", "是否允许玩家在游戏开始后进入观战。");
        comment(config, ConfigPath.ARENA_SPAWN_PROTECTION, "队伍出生点保护半径，单位为方块。");
        comment(config, ConfigPath.ARENA_SHOP_PROTECTION, "商店 NPC 周围保护半径，最小为 1 格；按方块坐标对称保护。");
        comment(config, ConfigPath.ARENA_UPGRADES_PROTECTION, "升级 NPC 周围保护半径，最小为 1 格；按方块坐标对称保护。");
        comment(config, ConfigPath.ARENA_GENERATOR_PROTECTION, "资源生成器周围保护半径。");
        comment(config, ConfigPath.ARENA_ISLAND_RADIUS, "队伍岛屿识别半径，用于陷阱、治疗池和自动找床。");
        comment(config, "worldBorder", "世界边界直径配置，单位为方块。");
        comment(config, ConfigPath.ARENA_Y_LEVEL_KILL, "低于此 Y 坐标时判定为掉入虚空。");
        comment(config, ConfigPath.ARENA_CONFIGURATION_MAX_BUILD_Y, "玩家允许放置方块的最大 Y 坐标。");
        comment(config, ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS, "队伍无成员且床已被摧毁时停止岛屿资源生成；包括开局未分配玩家的空队伍。", "队员暂时离岛或等待重生不会停产；新配置默认开启，已有值保持不变。");
        comment(config, ConfigPath.ARENA_DISABLE_NPCS_FOR_EMPTY_TEAMS, "空队伍是否不生成商店和升级 NPC。");
        comment(config, ConfigPath.ARENA_NORMAL_DEATH_DROPS, "是否使用原版死亡掉落；关闭时由插件管理资源掉落。");
        comment(config, ConfigPath.ARENA_USE_BED_HOLO, "是否在床上方显示床状态全息文字。");
        comment(config, ConfigPath.ARENA_ALLOW_MAP_BREAK, "是否允许破坏地图原有方块；关闭时通常只能破坏玩家放置的方块。");
        comment(config, ConfigPath.ARENA_GAME_RULES, "进入竞技场时应用的游戏规则，格式为 规则:值。", "竞技场初始化时固定正午 6000 tick 与晴天，并关闭受支持 Paper 版本的 advance_time、advance_weather、spawn_mobs、随机方块刻、火势蔓延和 Locator Bar。", "旧 doDaylightCycle 等 Bukkit 名称会自动映射到现代注册键；后续时间、天气以及 advance_time/advance_weather 的变化由事件守卫恢复，不使用周期扫描。");
        comment(config, "waiting.Loc", "等待大厅出生坐标，使用 x.5,y,z.5 的方块中心格式。");
        comment(config, ConfigPath.ARENA_WAITING_FACING, "进入等待大厅时使用的朝向；yaw 自动取最近的 90 度倍数，pitch 固定为 0。");
        comment(config, ConfigPath.ARENA_WAITING_POS1, "开局后移除地图内等待大厅区域的第一个角点。");
        comment(config, ConfigPath.ARENA_WAITING_POS2, "开局后移除地图内等待大厅区域的第二个角点。");
        comment(config, ConfigPath.ARENA_SPEC_LOC, "观战与虚空死亡回退位置。");
        comment(config, ConfigPath.ARENA_SPEC_FACING, "进入观战位置时使用的朝向；yaw 自动取最近的 90 度倍数，pitch 固定为 0。");
        comment(config, "generator.Diamond", "地图中央钻石生成器坐标列表。");
        comment(config, "generator.Emerald", "地图中央绿宝石生成器坐标列表。");
        comment(config, "Team", "队伍配置；每队包含颜色、出生点、床、NPC 和岛屿生成器。");

        ConfigurationSection teams = config.getYml().getConfigurationSection("Team");
        if (teams == null) return;
        for (String team : teams.getKeys(false)) {
            String root = "Team." + team + '.';
            comment(config, root + "Color", "队伍颜色，填写 TeamColor 枚举名称；青色使用 CYAN。", "旧值 AQUA 会自动迁移为 CYAN。");
            comment(config, root + "Spawn", "队伍出生和复活坐标。");
            comment(config, root + ConfigPath.ARENA_TEAM_SPAWN_FACING, "出生和复活时使用的朝向；yaw 自动取最近的 90 度倍数，pitch 固定为 0。");
            comment(config, root + "Bed", "队伍床的坐标；插件也可在设置出生点时自动识别。");
            comment(config, root + "Shop", "商店村民坐标。");
            comment(config, root + ConfigPath.ARENA_TEAM_SHOP_FACING, "商店村民朝向；yaw 自动取最近的 90 度倍数，pitch 固定为 0；游戏中位置和视线均锁定。");
            comment(config, root + "Upgrade", "升级村民坐标。");
            comment(config, root + ConfigPath.ARENA_TEAM_UPGRADE_FACING, "升级村民朝向；yaw 自动取最近的 90 度倍数，pitch 固定为 0；游戏中位置和视线均锁定。");
            comment(config, root + ConfigPath.ARENA_TEAM_KILL_DROPS_LOC, "队员最终死亡时资源掉落的位置。");
            comment(config, root + "Iron", "队伍岛屿铁生成器坐标列表。");
            comment(config, root + "Gold", "队伍岛屿金生成器坐标列表。");
            comment(config, root + "Emerald", "队伍岛屿绿宝石生成器坐标列表。");
        }
    }

    public static void generators(ConfigManager config) {
        comment(config, "Default", "默认分组的生成器参数；可以复制本节并将 Default 改为竞技场分组名。", "delay/start 单位为秒，amount 是每次生成数量，spawn-limit 按生成点附近同类地面积压总数计算。");
        comment(config, ConfigPath.GENERATOR_STACK_ITEMS, "是否在生成时直接合并同类资源，可减少物品实体数量。");
        String root = "Default.";
        comment(config, root + ConfigPath.GENERATOR_IRON_DELAY, "铁资源生成间隔，单位为秒；2.10.7 默认值为 1 秒。");
        comment(config, root + ConfigPath.GENERATOR_IRON_AMOUNT, "每次生成的铁数量。");
        comment(config, root + ConfigPath.GENERATOR_IRON_SPAWN_LIMIT, "生成点附近铁物品达到该总数后暂停生成，拾取后自动恢复；设为 0 表示不限制。");
        comment(config, root + ConfigPath.GENERATOR_GOLD_DELAY, "金资源生成间隔，单位为秒；2.10.7 默认值为 4 秒。");
        comment(config, root + ConfigPath.GENERATOR_GOLD_AMOUNT, "每次生成的金数量。");
        comment(config, root + ConfigPath.GENERATOR_GOLD_SPAWN_LIMIT, "生成点附近金物品达到该总数后暂停生成，拾取后自动恢复；设为 0 表示不限制。");
        commentTier(config, root, "diamond", "钻石");
        commentTier(config, root, "emerald", "绿宝石");
    }

    private static void commentTier(ConfigManager config, String root, String resource, String label) {
        for (String tier : List.of("tierI", "tierII", "tierIII")) {
            String path = root + resource + "." + tier + '.';
            comment(config, path + "delay", label + " " + tier + " 生成间隔，单位为秒。");
            comment(config, path + "amount", label + " " + tier + " 每次生成数量。");
            comment(config, path + "spawn-limit", label + " " + tier + " 生成点附近地面积压达到该总数后暂停生成，拾取后恢复；0 表示不限制。");
            if (!tier.equals("tierI")) comment(config, path + "start", "本等级在开局后多少秒启用。");
        }
    }

    public static void levels(ConfigManager config) {
        comment(config, "levels", "等级显示名称和升级经验；键支持单个等级或 5-10 形式的范围。", "name 支持 & 颜色代码和 {number} 等级占位符。");
        comment(config, "levels.1.name", "该等级在聊天和计分板中的显示格式。");
        comment(config, "levels.1.rankup-cost", "升到下一级所需经验值。");
        comment(config, "xp-rewards.per-minute", "每游戏分钟获得的经验。");
        comment(config, "xp-rewards.per-teammate", "每名队友带来的经验奖励。");
        comment(config, "xp-rewards.game-win", "获胜经验奖励。");
        comment(config, "xp-rewards.bed-destroyed", "摧毁床的经验奖励。");
        comment(config, "xp-rewards.regular-kill", "普通击杀经验奖励。");
        comment(config, "xp-rewards.final-kill", "最终击杀经验奖励。");
        comment(config, "progress-bar.symbol", "经验进度条使用的字符。");
        comment(config, "progress-bar.unlocked-color", "已完成进度的颜色。");
        comment(config, "progress-bar.locked-color", "未完成进度的颜色。");
        comment(config, "progress-bar.format", "进度条整体格式，{progress} 会替换为进度字符。");
    }

    public static void rewards(ConfigManager config) {
        comment(config, "money-rewards", "Vault 与实际经济服务提供者均可用时发放的金币奖励；只安装 Vault 不会创建余额。");
        comment(config, "money-rewards.per-minute", "每游戏分钟奖励的金币。");
        comment(config, "money-rewards.per-teammate", "每名队友带来的金币奖励。");
        comment(config, "money-rewards.game-win", "游戏获胜金币奖励。");
        comment(config, "money-rewards.bed-destroyed", "摧毁床的金币奖励。");
        comment(config, "money-rewards.final-kill", "最终击杀金币奖励。");
        comment(config, "money-rewards.regular-kill", "普通击杀金币奖励。");
    }

    public static void signs(ConfigManager config) {
        comment(config, "format", "竞技场加入告示牌的四行文本。", "占位符：[arena] [on] [max] [type] [status]。");
        comment(config, ConfigPath.SIGNS_STATUS_BLOCK_WAITING_MATERIAL, "等待状态的告示牌背板 Bukkit Material。");
        comment(config, ConfigPath.SIGNS_STATUS_BLOCK_STARTING_MATERIAL, "倒计时状态的告示牌背板 Bukkit Material。");
        comment(config, ConfigPath.SIGNS_STATUS_BLOCK_PLAYING_MATERIAL, "游戏中状态的告示牌背板 Bukkit Material。");
        comment(config, ConfigPath.SIGNS_STATUS_BLOCK_RESTARTING_MATERIAL, "重置中状态的告示牌背板 Bukkit Material。");
        comment(config, "locations", "已注册告示牌的位置列表，由插件自动维护，不建议手动编辑。");
    }

    public static void sounds(ConfigManager config) {
        for (String path : config.getYml().getKeys(false)) {
            if (path.equals(ConfigManager.CONFIG_VERSION_PATH)) continue;
            comment(config, path, "事件声音 “" + path + "”：sound 为 Bukkit Sound，volume 为音量，pitch 为音调。", "将调用方的声音路径设为 none 可关闭对应声音。");
        }
    }

    public static void upgrades(ConfigManager config) {
        comment(config, "default-upgrades-settings", "升级菜单布局和陷阱规则：menu-content 为 元素,槽位；价格货币支持 iron、gold、diamond、emerald 或 Vault。");
        comment(config, "upgrade-swords", "锋利 I–IV 逐级购买；默认价格为 4、8、16、32 钻石。", "每个 tier 包含价格、货币、显示物品和 receive 动作。");
        comment(config, "upgrade-armor", "保护 I–IV 逐级购买；默认价格为 2、4、10、24 钻石。");
        comment(config, "upgrade-miner", "挖掘急迫升级。");
        comment(config, "upgrade-forge", "岛屿资源生成器升级。");
        comment(config, "upgrade-heal-pool", "基地治疗池升级。");
        comment(config, "upgrade-dragon", "决战阶段额外末影龙升级。");
        comment(config, "category-traps", "陷阱分类及其菜单槽位。");
        comment(config, "trap-slot-first", "第一个陷阱队列槽位。");
        comment(config, "trap-slot-second", "第二个陷阱队列槽位。");
        comment(config, "trap-slot-third", "第三个陷阱队列槽位。");
        for (int index = 1; index <= 4; index++) {
            comment(config, "base-trap-" + index, "陷阱 " + index + " 的显示物品和触发动作。");
        }
        comment(config, "separator-glass", "升级菜单的装饰分隔物品。");
        comment(config, "separator-back", "返回按钮及玩家/控制台点击命令。");
    }

    public static void shop(ConfigManager config) {
        comment(config, "shop-settings", "商店菜单的快捷购买按钮、空槽和分类分隔物品设置。");
        comment(config, ConfigPath.SHOP_SPECIAL_SILVERFISH_ENABLE, "蠹虫道具：启用状态、材质、生命、伤害、速度和存活秒数。");
        comment(config, ConfigPath.SHOP_SPECIAL_IRON_GOLEM_ENABLE, "铁傀儡道具：启用状态、材质、生命、速度和存活秒数。");
        comment(config, ConfigPath.SHOP_SPECIAL_TOWER_ENABLE, "防御塔道具：启用状态和触发材质。");
        comment(config, ConfigPath.SHOP_QUICK_DEFAULTS_PATH, "新玩家快捷购买栏；path 指向商品，slot 是菜单槽位。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_BLOCKS, "方块分类。分类含菜单槽位、显示物品和 category-content 商品。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_MELEE, "近战武器分类。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_ARMOR,
                "永久护甲分类；商店固定只出售护腿和靴子，保留本队颜色的皮革头盔与胸甲。",
                "weight 用于阻止购买更低等级护甲；旧版上半身商品会自动删除。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_TOOLS, "工具分类；permanent/downgradable 控制永久保留和死亡降级。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_RANGED, "弓箭分类。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_POTIONS, "药水分类；potion 格式为 效果,持续秒数,等级。");
        comment(config, ConfigPath.SHOP_PATH_CATEGORY_UTILITY, "实用道具分类，包括 TNT、火球、珍珠、搭桥蛋、袖珍弹出塔和回城卷轴。", "回城卷轴默认位于槽位 32，价格为 3 钻石；bedwars-item 标识用于绑定核心行为，请勿删除。");
    }

    private static void comment(ConfigManager config, String path, String... lines) {
        config.setComments(path, lines);
    }
}
