# 🍽️ 黑马点评（DianPing Clone）

一个基于 Spring Boot + Redis + MySQL 构建的本地生活点评系统，支持用户登录、商户浏览、优惠券秒杀、缓存优化等核心功能。

> 📌 项目重点：高并发处理 + 缓存设计 + 分布式锁 + 秒杀系统

---

## 🚀 项目亮点

- 🔥 Redis 多种数据结构实战（String / Hash / Set / ZSet / Bitmap）
- ⚡ 秒杀系统：Redis + Lua 实现原子扣减库存
- 🧠 缓存优化：缓存穿透、缓存击穿、缓存雪崩解决方案
- 🔒 分布式锁：基于 Redis 实现高并发控制
- 📍 GEO 数据：实现“附近商户”功能
- 📊 UV 统计：基于 Bitmap 实现高效统计
- 🧵 异步处理：使用消息队列思想（或线程池）提升性能
- 📦 登录优化：Token + Redis 替代 Session

---

**总体架构**

```mermaid
graph TD
    A[Client 前端] --> B[Nginx 反向代理]
    B --> C[Spring Boot 应用]

    C --> D[(MySQL 数据库)]
    C --> E[(Redis 缓存层)]

    E --> E1[缓存数据]
    E --> E2[分布式锁]
    E --> E3[秒杀库存]
    E --> E4[GEO位置]
    E --> E5[Bitmap统计]

    C --> F[线程池 / 异步任务]

    F --> D
```
---

## 🧩 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | 后端核心框架 |
| MySQL | 数据存储 |
| Redis | 缓存 / 分布式锁 / 秒杀 |
| MyBatis-Plus | ORM 框架 |
| Nginx | 反向代理 |
| Docker（可选） | 部署环境 |
| Lua | 原子操作（秒杀） |

---

## 📦 核心功能

### 👤 用户模块
- 手机号验证码登录
- Token 存储 Redis
- 登录态校验拦截器

### 🏪 商户模块
- 商户分类查询
- 商户详情
- 商户缓存（缓存穿透解决）

### 🎯 优惠券秒杀
- 高并发秒杀
- Redis 预减库存
- Lua 保证原子性
- 一人一单控制

### 📍 附近商户
- 使用 Redis GEO 实现距离排序

### 📊 数据统计
- UV 统计（Bitmap）
- 点赞功能（Set / ZSet）

---

## ⚙️ 缓存设计

### 缓存穿透
- 使用 **空值缓存**
```mermaid
flowchart TD
    A[请求数据] --> B{Redis是否存在?}

    B -- 有 --> C[直接返回缓存]

    B -- 无 --> D{是否缓存空值?}

    D -- 是 --> E[返回空]

    D -- 否 --> F[加锁查询数据库]
    F --> G[写入缓存]
    G --> H[返回数据]
```

### 缓存击穿
- 使用 **互斥锁**

```mermaid
flowchart TD
    A[客户端请求查询缓存] --> B[Redis查询缓存]
    B --> C{缓存是否存在?}

    C -->|存在| D[直接返回缓存数据]
    C -->|不存在| E[尝试获取互斥锁]

    E --> F{是否获取成功?}
    F -->|否| G[休眠一段时间后重试]
    G --> B

    F -->|是| H[查询数据库]
    H --> I{数据库是否有数据?}

    I -->|有| J[写入Redis缓存并设置TTL]
    I -->|无| K[写入空值到Redis并设置较短TTL]

    J --> L[释放互斥锁]
    K --> L
    L --> M[返回结果]
```

- **逻辑过期**
```mermaid
flowchart TD
    A[客户端请求查询缓存] --> B[Redis查询缓存]
    B --> C{缓存是否存在?}

    C -->|不存在| D[返回空或降级处理]
    C -->|存在| E[判断逻辑过期时间]

    E --> F{是否过期?}
    F -->|未过期| G[直接返回缓存数据]
    F -->|已过期| H[尝试获取互斥锁]

    H --> I{是否获取成功?}
    I -->|否| J[直接返回旧数据]

    I -->|是| K[再次查询Redis缓存]
    K --> L{缓存是否仍然逻辑过期?}

    L -->|否| M[直接返回最新缓存]
    L -->|是| N[开启独立线程重建缓存]

    N --> O[查询数据库]
    O --> P[写入新缓存并更新逻辑过期时间]
    P --> Q[释放锁]

    J --> R[返回旧数据]
    M --> R
    G --> R
    Q --> R
```

### 缓存雪崩
- TTL 随机值
- 多级缓存

---

## ⚡ 秒杀系统设计

核心流程：

1. 用户请求秒杀接口
2. Redis Lua 脚本执行：
   - 判断库存
   - 判断是否重复下单
   - 扣减库存
3. 返回抢购结果
4. 异步创建订单（防止阻塞）

---

## 🔒 分布式锁实现

- 基于 Redis SETNX 实现
- 使用 UUID 防止误删锁
- Lua 脚本保证释放锁的原子性

```mermaid
flowchart TD
    A[请求进入] --> B[生成唯一标识 UUID]

    B --> C[尝试加锁 SETNX key value EX ttl]

    C -->|成功| D[执行业务逻辑]
    C -->|失败| E[自旋/等待/重试]

    E --> C

    D --> F{业务是否执行成功?}

    F -->|是| G[释放锁 Lua脚本]
    F -->|否| G

    G --> H{校验value是否一致?}

    H -->|是| I[删除锁 DEL]
    H -->|否| J[不删除（避免误删）]

    I --> K[返回成功]
    J --> K

    K --> L[结束]
```
---

## 🧠 项目优化点

- 使用 Redis 替代 Session，提升性能
- 秒杀场景避免超卖问题
- 使用缓存提高查询性能
- 使用线程池
- 合理设计数据库索引

---

## 📁 项目结构


├── controller
├── service
├── mapper
├── entity
├── config
├── utils
|—— dto


---

分层讲解

1️⃣ 网关层（Nginx）
反向代理
负载均衡

2️⃣ 应用层（Spring Boot）
处理业务逻辑
提供 REST API
控制流程（秒杀、缓存、登录）

3️⃣ 缓存层（Redis）

👉 核心亮点：

缓存热点数据（减少 DB 压力）
分布式锁（控制并发）
秒杀库存（高并发核心）
GEO（附近商户）
Bitmap（UV统计）

4️⃣ 数据层（MySQL）
持久化数据
最终一致性

5️⃣ 异步层（线程池）

👉 用来：

削峰
解耦业务
防止请求阻塞

## 🛠️ 启动方式

```bash
 1. 克隆项目
git clone https://github.com/kamisheng/hmdp-kami.git

 2. 启动 Redis
redis-server

 3. 修改 application.yml 数据库配置

 4. 启动项目
mvn spring-boot:run
```
📸 项目截图（可选）

这里可以放接口测试图 / 前端页面截图

📌 后续优化方向
引入 Kafka / RabbitMQ 实现真正异步削峰
使用 Redisson 优化分布式锁
引入 ElasticSearch 实现搜索
微服务拆分（Spring Cloud）
接入限流组件（Sentinel）

👨‍💻 作者
GitHub: https://github.com/kamisheng
📄 License

MIT


---
