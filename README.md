## Redis缓存问题  

### ✅ 缓存雪崩解决方案：逻辑过期 + 异步刷新

#### 📌 问题
大量缓存同时过期 → 请求穿透到 DB → 数据库压力剧增（雪崩）

#### 🔧 核心思想
- **缓存永不过期（物理不过期）**
- 数据中包含 **逻辑过期时间**
- **请求只读缓存，不重建**
- **后台异步线程定期刷新(异步刷新，建议自己定义线程池---线程池的实际使用场景)**  

redis缓存的常见问题(击穿/穿透/雪崩)，核心问题是大量请求到达数据库，而数据库的性能无法处理大量请求。  
所以，请求只读缓存，不重建缓存，就可以直接断开高并发请求和数据库操作之间的联系(解耦)。  


#### 🛠 实现要点
1. **包装缓存结构**
   ```java
   class CachedData<T> {
       T data;
       LocalDateTime expireTime; // 逻辑过期时间
   }
   ```

2. **读缓存逻辑**
   - 未过期 → 直接返回
   - 已过期 → 返回旧数据 + 触发异步刷新（优雅降级）
   - 缓存为空 → 可触发首次异步加载

3. **异步刷新（@Async + 分布式锁）**
   - 加 Redis 锁防并发刷新
   - 从 DB 加载 → 更新缓存（无 Redis TTL）
   - 空值也缓存（防穿透）

4. **配置**
   - `@EnableAsync` + 自定义线程池
   - 刷新周期：如 5 分钟（逻辑过期时间）

#### ✅ 优势
- 彻底避免缓存雪崩 & 击穿  
- 请求零 DB 压力  
- 高可用：过期时仍返回旧数据

#### ⚠️ 注意
- 首次访问需处理冷启动（预热 or 异步加载）
- 监控刷新成功率
- 冷数据需定期清理（防 Redis 膨胀）

--- 

> 💡 一句话总结：**缓存永不过期，后台悄悄刷新，请求只读不写，雪崩无处可逃。**

---

✅ 4. 数据库限流 & 降级
在 productMapper.findById() 前增加熔断/限流（如 Sentinel）
防止 DB 被打垮

# Sentinel 在 Spring Cloud 架构中的正确使用方式（精简版）

在 Spring Cloud 微服务架构中，**Sentinel 的集成方式需根据服务类型（响应式 vs 阻塞式）区别对待**。以下是针对 **Spring Cloud Gateway** 和 **后端业务服务（如 product-service）** 的核心要点与架构思路。

---

## 1. Spring Cloud Gateway（网关层）

### ❌ 不适用
- `@SentinelResource` 注解
- 传统 Sentinel 阻塞式 API（如 `SphU.entry()`）

> 原因：Gateway 基于 WebFlux（响应式），与 Sentinel 的 Servlet 模型不兼容。

### ✅ 正确思路
- 引入 **`sentinel-spring-cloud-gateway-adapter`** 依赖
- 使用 **`GatewayFlowRule`** 定义限流规则（按 Route ID 或自定义 API 分组）
- 通过 **`GatewayRuleManager.loadRules()`** 加载规则
- 自定义限流响应：使用 **`GatewayCallbackManager.setBlockHandler()`**

### 🎯 职责
- **入口流量控制**：限制进入下游服务的总 QPS，防止系统被突发流量压垮

---

## 2. Product-Service（业务服务层）

### ✅ 适用场景
- 服务基于 `spring-boot-starter-web`（即传统 Tomcat + Servlet 模型）
- 需保护高风险操作（如数据库查询）

### ✅ 正确思路
- 引入 **Sentinel Core + 注解切面** 依赖
- 启用 **`SentinelResourceAspect`** 切面
- 在关键方法（如 `findById`）上使用 **`@SentinelResource`**
  - 通过 `blockHandler` 处理限流/熔断
  - 通过 `fallback` 处理异常降级
- 通过 **`FlowRuleManager` / `DegradeRuleManager`** 配置 QPS 限流或慢调用熔断规则

### 🎯 职责
- **精细化防护**：防止缓存失效时大量请求穿透到数据库
- **与缓存策略协同**：结合空值缓存、互斥锁、随机过期，形成完整防护体系

---

## 🧩 整体架构原则

| 层级 | Sentinel 作用 | 关键类/机制 |
|------|-------------|------------|
| **Gateway** | 全局限流（入口） | `GatewayFlowRule`, `GatewayRuleManager` |
| **Product-Service** | 局部熔断/限流（DB 保护） | `@SentinelResource`, `FlowRuleManager`, `DegradeRuleManager` |

> ✅ **分层防御**：网关防“洪”，服务防“崩”，两者缺一不可。

---

## 🏗️ 架构设计思路

### 1. **分层防护，职责分离**
- **网关层**：作为系统第一道防线，控制**整体入口流量**，避免下游服务集群被瞬间打满。
- **服务层**：作为第二道防线，针对**具体高危操作**（如查 DB、调第三方）做精细化熔断与限流，防止局部故障扩散。

### 2. **缓存 + Sentinel 协同防御缓存问题**
- **缓存穿透**：空值缓存 + Sentinel 限制 DB 查询频率
- **缓存击穿**：互斥锁重建缓存 + Sentinel 防止锁竞争期间 DB 被压垮
- **缓存雪崩**：随机过期时间 + Sentinel 熔断慢查询，避免 DB 集中失效

### 3. **防御纵深（Defense in Depth）**
- 即使网关限流失效（如规则配置错误），服务层仍能兜底保护数据库；
- 即使服务层缓存失效，Sentinel 也能阻止 DB 被海量请求击穿。

### 4. **可观测性与动态治理**
- 结合 Sentinel Dashboard，**动态调整**网关和服务的限流阈值；
- 通过日志和监控，识别高频被限流的接口，持续优化缓存策略和资源配额。

---

> 💡 **核心思想**：  
> **网关管“量”，服务管“质”** —— 网关控制总流量规模，服务保障关键操作稳定性。  
> 二者协同，构建高可用、抗压、自愈的微服务系统。
