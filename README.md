# DsBoss - 自定义BOSS插件

基于 Paper/Purpur 1.21+ 的可配置 BOSS 插件，支持定时生成、自定义掉落、经济奖励、全服播报与超时自动清理。

作者：DobeShadow

## 功能

- **自定义 BOSS** — 实体类型、名称、血量、攻击力、药水效果、装备自由配置
- **定时生成** — 北京时间多时间段，检测间隔可配
- **掉落物发放** — 直接发放到参与攻击的玩家背包，支持种类 / 数量 / 概率
- **最后一击奖励** — 独立经济奖励（Vault）+ 命令执行 + 私聊消息
- **全服奖励** — BOSS 击杀后所有在线玩家获得金币 / 执行命令
- **全服播报** — 生成与死亡时多行自定义播报，支持变量占位符
- **BOSS 血条** — 自动显示血量进度条，血量超过 1024 时自动启用自定义追踪
- **超时清理** — BOSS 存活超时且无人挑战时自动移除并全服播报
- **管理员命令** — 手动生成 / 击杀 / 查看 / 重载

## 构建

```bash
mvn clean package
```

生成的 JAR 在 `target/DsBoss-1.0.2.jar`。

## 安装

1. 将 `DsBoss-1.0.2.jar` 放入服务器的 `plugins/` 目录
2. 重启服务器或执行 `/reload confirm`
3. 编辑 `plugins/DsBoss/config.yml` 配置 BOSS
4. 执行 `/dsboss reload` 重载配置

## 依赖

- **必需**: Paper/Purpur 1.21+
- **可选**: [Vault](https://www.spigotmc.org/resources/vault.34315/) + 任意经济插件（用于金币奖励）

### 血量上限说明

Minecraft 1.21+ 将原版实体血量上限硬编码为 1024，无法通过 `spigot.yml` 等配置突破。

DsBoss 已内置**自定义血量追踪**：当配置血量超过实体实际上限时，插件自动以自定义血量计算伤害，血条显示配置的真实血量（如 2000），无需任何额外配置即可生效。

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/dsboss reload` | 重新加载配置 | `dsboss.admin` |
| `/dsboss list` | 列出所有 BOSS 及状态 | `dsboss.admin` |
| `/dsboss spawn <ID> [world x y z]` | 手动生成 BOSS | `dsboss.admin` |
| `/dsboss kill <ID>` | 强制击杀 BOSS | `dsboss.admin` |
| `/dsboss info <ID>` | 查看 BOSS 详情 | `dsboss.admin` |

别名: `/cboss`, `/cb`

## 配置文件

```yaml
# plugins/DsBoss/config.yml

check-interval: 30          # 定时检测间隔（秒）
timezone: "Asia/Shanghai"   # 时区
enable-boss-bar: true       # 显示 BOSS 血条

auto-despawn:               # 超时清理（可选）
  minutes: 120              # 存活超时（分钟），0 = 不清理
  idle-minutes: 10          # 最后被攻击空闲多久后清理（0 = 到点直接清）
  broadcast: "&e{boss_name} &7长时间无人挑战，已消失……"

bosses:
  dragon_boss:              # BOSS 唯一 ID
    enabled: true
    type: ZOMBIE            # 实体类型
    name: "&c&l远古巨龙"    # 彩色名称
    health: 2000.0          # 血量
    damage: 15.0            # 攻击力
    spawn-location:         # 生成位置
      world: world
      x: 0.0
      y: 64.0
      z: 0.0
    spawn-times:            # 北京时间生成时段
      - "12:00"
      - "18:00"
      - "21:00"
    equipment:              # 装备（可选）
      helmet: DIAMOND_HELMET
      chestplate: DIAMOND_CHESTPLATE
      leggings: DIAMOND_LEGGINGS
      boots: DIAMOND_BOOTS
      main-hand: DIAMOND_SWORD
    fire-immune: true       # 免疫火焰
    fall-immune: true       # 免疫摔落
    potion-effects:         # 药水效果
      - type: SPEED
        level: 2
        duration: 999999
    last-hit-rewards:       # 最后一击奖励
      economy: 10000.0      # 金币（Vault）
      commands:             # 执行命令 [player]=玩家名
        - "give [player] diamond 5"
      messages:             # 私聊消息
        - "&a你击杀了BOSS！"
    server-rewards:         # 全服奖励
      economy: 2000.0       # 每人金币
      commands: []
      broadcast:            # 全服播报
        - "&e全服玩家获得2000金币！"
    drops:                  # 掉落物（发到参与玩家背包）
      - material: DIAMOND
        amount: 5
        chance: 1.0         # 概率 0.0~1.0
      - material: NETHERITE_INGOT
        amount: 1
        chance: 0.1
    broadcast-on-spawn:     # 生成播报
      - "&c[BOSS] {boss_name} 已生成！"
      - "&c[BOSS] 位置: {world} ({x}, {y}, {z})"
    broadcast-on-death:     # 击杀播报
      - "&c[BOSS] {killer} 击杀了 {boss_name}！"
```

## 播报变量

| 变量 | 说明 |
|------|------|
| `{boss_name}` | BOSS 名称 |
| `{boss_type}` | 实体类型 |
| `{health}` | 当前血量 |
| `{max_health}` | 最大血量 |
| `{world}`, `{x}`, `{y}`, `{z}` | 位置坐标 |
| `{killer}` | 击杀者名称 |
| `{time}` | 当前北京时间 (HH:mm) |
| `{timezone}` | 时区名称 |

## 项目结构

```
├── pom.xml
└── src/main/
    ├── java/com/dobeshadow/dsboss/
    │   ├── DsBoss.java             # 主类，Vault 经济集成
    │   ├── boss/
    │   │   ├── BossConfig.java     # BOSS 配置 POJO
    │   │   └── BossManager.java    # 生成/奖励/调度/超时管理
    │   ├── commands/
    │   │   └── BossCommand.java    # 管理命令
    │   └── listener/
    │       └── BossListener.java   # 事件监听
    └── resources/
        ├── plugin.yml
        └── config.yml
```

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `dsboss.admin` | OP | 使用管理命令 |
| `dsboss.use` | 所有人 | 参与 BOSS 事件 |

## License

MIT
