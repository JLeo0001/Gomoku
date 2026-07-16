# Gomoku（五子棋）

适用于 Purpur 1.21+ 的五子棋小游戏插件。创建场地后全自动搭建棋盘、等待区、观战台，开箱即用。

## 特性

- **PvP 与 PvE** — 玩家对战与人机对战，AI 采用迭代加深 Negamax + α-β 剪枝，具备威胁检测和走法排序
- **全自动搭建** — 创建场地后自动生成独立世界、棋盘、等待区、玩家站位和观战台，无需手动建造
- **配置驱动** — 修改 `config.yml` 后 `/gka reload`，所有场地棋盘自动重建
- **可配置棋盘** — 大小（9-25）、Y 坐标、表面方块、边框方块均可配置（默认 19×19 平滑石 + 深色橡木边框）
- **骷髅头颅棋子** — 白棋为骷髅头颅，黑棋为凋零骷髅头颅
- **观战系统** — 旁观对局（玻璃观战台），可限制最大观战人数，观战聊天隔离
- **全自动流程** — 大厅倒计时、回合计时、自动判胜、超时判负、自动清理
- **背包保护** — 进入游戏自动保存背包/经验/状态，离开自动恢复，游戏内禁止破坏/伤害/掉落
- **断线保护** — 玩家中途退出/被踢/切换世界自动判定弃权并恢复
- **多语言** — 内置中文和英文，所有提示可自定义
- **无前置依赖** — 无资源包，不需要任何前置插件

## 快速开始

1. 将 `Gomoku-1.2.0.jar` 放入 `plugins/` 目录，启动服务器
2. `/gka create arena1` — 创建场地（世界、棋盘、等待区、观战台全部自动生成）
3. 玩家 `/gomoku join pvp` 开始对战，或 `/gomoku join pve` 挑战 AI

## 指令

### 玩家指令 `/gomoku`（别名 `/gk` `/go`）

| 指令 | 说明 |
|---|---|
| `/gomoku join pvp [场地]` | 加入 PvP 匹配 |
| `/gomoku join pve [场地]` | 加入 PvE 人机对战 |
| `/gomoku leave` | 离开当前游戏 |
| `/gomoku spectate [场地]` | 观战（再次使用退出观战） |
| `/gomoku version` | 查看版本 |

### 管理员指令 `/gomokuadmin`（别名 `/gka`）

| 指令 | 说明 |
|---|---|
| `/gka create <id>` | 创建场地 |
| `/gka delete <id>` | 删除场地 |
| `/gka set <id> <点位>` | 手动微调点位（lobby/spawn1/spawn2/spectator） |
| `/gka maxspec <id> <数量>` | 设置最大观战人数 |
| `/gka status [id]` | 查看场地状态 |
| `/gka list` | 列出所有场地 |
| `/gka end <id\|all>` | 强制结束对局 |
| `/gka reload` | 重载配置并重建所有棋盘 |

## 权限

| 权限节点 | 说明 | 默认 |
|---|---|---|
| `gomoku.player` | 玩家指令 | 所有人 |
| `gomoku.admin` | 管理员指令 | OP |
| `gomoku.spectate` | 观战 | 所有人 |

## 配置

`plugins/Gomoku/config.yml`：

```yaml
board:
  size: 19                     # 棋盘大小（9-25）
  y-level: 64                  # 棋盘 Y 坐标
  surface-block: SMOOTH_STONE  # 棋盘表面方块
  grid-block: DARK_OAK_PLANKS  # 棋盘边框方块

timing:
  lobby-countdown: 30          # 大厅倒计时（秒）
  turn-timeout: 60             # 回合计时（秒）
  game-max-duration: 1800      # 最大对局时间（秒）

spectator:
  max-per-arena: 10            # 单场地最大观战人数
```

## 场地结构

创建场地后自动生成：

```
         [观战台] 玻璃平台 + 围栏（棋盘上方 8 格）

  [白棋]                           [黑棋]
  台阶                              台阶

  ╔═══════════════════╗
  ║  平滑石 棋盘 N×N  ║
  ╚═══════════════════╝
      深色橡木边框

            [等待区]
         石砖平台 + 信标
```

## 语言

`plugins/Gomoku/Language/zh_CN.yml` 和 `en_US.yml`，所有提示均可自定义。

## 构建

```bash
./gradlew shadowJar
# 输出：build/libs/Gomoku-1.2.0.jar
```

需求：JDK 21、Paper/Purpur 1.21+
