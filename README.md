# Gomoku（五子棋）

一款适用于 Purpur 1.21+ 的五子棋小游戏插件，支持 PvP 对战与 PvE 人机模式。

## 特性

- **PvP 与 PvE** — 玩家对战与人机对战（内置 AI，基于极小化极大 + α-β 剪枝）
- **独立维度** — 每个场地拥有独立的 flat 世界，互不干扰
- **可配置棋盘** — 棋盘大小、表面方块均可配置（默认 19×19，橡木木板）
- **骷髅头颅棋子** — 白棋为骷髅头颅，黑棋为凋零骷髅头颅
- **观战系统** — 支持旁观对局，可限制最大观战人数，观战聊天隔离
- **全自动流程** — 大厅倒计时、回合计时、自动判胜、自动清理
- **背包保护** — 进入游戏自动保存背包，离开自动恢复，游戏内禁止破坏/伤害/掉落
- **多语言支持** — 所有提示可自定义，内置中文和英文
- **无前置依赖** — 无资源包，开箱即用

## 指令

### 玩家指令 `/gomoku`（别名：`/gk` `/go`）

| 指令 | 说明 |
|---|---|
| `/gomoku join pvp [场地]` | 加入 PvP 匹配 |
| `/gomoku join pve [场地]` | 加入 PvE 人机对战 |
| `/gomoku leave` | 离开当前游戏 |
| `/gomoku spectate [场地]` | 观战 |
| `/gomoku version` | 查看版本 |

### 管理员指令 `/gomokuadmin`（别名：`/gka`）

| 指令 | 说明 |
|---|---|
| `/gka create <id>` | 创建场地（自动生成独立世界） |
| `/gka delete <id>` | 删除场地 |
| `/gka set <id> <点位>` | 设置场地功能点 |
| `/gka maxspec <id> <数量>` | 设置最大观战人数 |
| `/gka status [id]` | 查看场地状态 |
| `/gka list` | 列出所有场地 |
| `/gka end <id\|all>` | 强制结束对局 |
| `/gka reload` | 重载配置 |

### 点位说明

| 点位 | 说明 |
|---|---|
| `board1` | 棋盘第一个角（最小坐标） |
| `board2` | 棋盘对角（最大坐标） |
| `lobby` | 大厅等待点 |
| `spawn1` | 白棋玩家站位 |
| `spawn2` | 黑棋玩家站位 |
| `spectator` | 观战区 |

## 权限

| 权限节点 | 说明 | 默认 |
|---|---|---|
| `gomoku.player` | 玩家指令权限 | 所有人 |
| `gomoku.admin` | 管理员指令权限 | OP |
| `gomoku.spectate` | 观战权限 | 所有人 |

## 搭建步骤

1. 将插件放入 `plugins/` 目录，启动服务器
2. `/gka create arena1` — 创建场地
3. 进入生成的场地世界，设置各点位（站在目标位置执行）：
   - `/gka set arena1 board1`
   - `/gka set arena1 board2`
   - `/gka set arena1 lobby`
   - `/gka set arena1 spawn1`
   - `/gka set arena1 spawn2`
   - `/gka set arena1 spectator`
4. `/gka status arena1` — 确认场地就绪
5. 玩家使用 `/gomoku join pvp` 即可开始

## 配置

`plugins/Gomoku/config.yml`：

```yaml
board:
  size: 19                     # 棋盘大小（9-25）
  surface-block: OAK_PLANKS    # 棋盘表面方块
  grid-block: DARK_OAK_PLANKS  # 棋盘边框方块

timing:
  lobby-countdown: 30          # 大厅倒计时（秒）
  turn-timeout: 60             # 回合计时（秒）
  game-max-duration: 1800      # 最大对局时间（秒）

spectator:
  max-per-arena: 10            # 单场地最大观战人数
```

## 语言文件

`plugins/Gomoku/Language/zh_CN.yml` 和 `en_US.yml`，所有游戏内提示均可修改。

## 构建

```bash
./gradlew shadowJar
# 输出：build/libs/Gomoku-1.2.0.jar
```

需求：JDK 21、Paper/Purpur 1.21+
