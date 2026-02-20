
# MySQL Connector/J PreparedStatement Analysis & Benchmarking

본 프로젝트는 MySQL Connector/J 드라이버 옵션에 따른 `PreparedStatement`의 실제 동작 메커니즘을 분석하고, 네트워크 프로토콜 및 서버 리소스 변화를 실험을 통해 검증합니다.

---

## 1. 개요: PreparedStatement의 오해

일반적으로 `PreparedStatement`는 사용 즉시 SQL 실행 계획이 재사용되어 성능이 향상되는 것으로 알려져 있습니다. 그러나 **MySQL Connector/J의 기본 설정(Default)에서 PreparedStatement는 서버 측에서 준비(Prepare)되지 않습니다.**

* **Default (`useServerPrepStmts=false`)**: 드라이버 레벨에서 파라미터 문자열을 치환하여 전송하는 **Client-side PreparedStatement**로 동작합니다.
* **Problem**: 매 실행 시 서버는 SQL 파싱 및 실행 계획을 새로 생성하므로, 성능 이점보다 보안(SQL Injection 방지) 목적에 국한됩니다.

---

## 2. 드라이버 구현체 및 프로토콜

### 2.1 Client-side PreparedStatement

* **옵션**: `useServerPrepStmts=false`
* **구현체**: `com.mysql.cj.jdbc.ClientPreparedStatement`
* **프로토콜**: Text Protocol (`COM_QUERY`)
* **특징**: 클라이언트 메모리에서 완성이 된 SQL 문자열을 서버로 전송합니다.

### 2.2 Server-side PreparedStatement

* **옵션**: `useServerPrepStmts=true`
* **구현체**: `com.mysql.cj.jdbc.ServerPreparedStatement`
* **프로토콜**: Binary Protocol (`COM_STMT_PREPARE`, `COM_STMT_EXECUTE`)
* **특징**: 서버에 SQL 구조를 전송하여 실행 계획을 생성하고 `Statement ID`를 발급받아 통신합니다.

---

## 3. 실험 설계

### 3.1 시나리오 (4 Cases)

| Case | `useServerPrepStmts` | `cachePrepStmts` | 동작 특징 |
| --- | --- | --- | --- |
| **1** | `false` | `false` | 기본값. 매번 전체 SQL 문자열 전송 |
| **2** | `true` | `false` | 서버 사이드 동작하나 매번 Prepare-Close 수행 (성능 저하 우려) |
| **3** | **`true`** | **`true`** | **최적화.** 서버 사이드 실행 계획 + 드라이버 객체 재사용 |
| **4** | `false` | `true` | 클라이언트 사이드 객체만 캐싱 |

> **Note**: 본 실험의 쿼리는 256자를 초과하므로, 정확한 캐싱 테스트를 위해 `prepStmtCacheSqlLimit=2048` 옵션을 적용하였습니다.

### 3.2 실험 환경

* **Stack**: Java 17, MySQL 8.0.x, MySQL Connector/J 8.0.x
* **Target**: Sakila DB (8개 테이블 JOIN 및 집계 함수 포함 쿼리)

---

## 4. 핵심 검증 포인트

### 4.1 구현체 확인 및 객체 캐싱 테스트

```java
// 1. 구현체 타입 확인
System.out.println(testStmt.getClass().getName()); 

// 2. 객체 재사용(Address 비교) 확인
System.out.println("캐싱 여부: " + (testStmt == testStmt2));

```

### 4.2 서버 사이드 Prepare 상태 추적

```sql
-- 서버 메모리에 할당된 Prepared Statement 개수 실시간 확인
SHOW GLOBAL STATUS LIKE 'Prepared_stmt_count';

```

---

## 5. 결과 분석 및 제언

### 5.1 성능 측정 결과 요약

1. **SQL 복잡도와 효율**: 단순 쿼리보다 **복잡한 JOIN 쿼리**에서 실행 계획 재사용을 통한 성능 이득이 극대화됩니다.
2. **캐싱 설정의 필수성**: `useServerPrepStmts=true`만 설정하고 캐시를 끄면(Case 2), 매 실행마다 Prepare/Close 통신 오버헤드가 발생하여 성능이 오히려 하락합니다.
3. **리소스 최적화**: 최적화 환경(Case 3)에서 서버 CPU 부하 감소 및 어플리케이션의 GC 효율 향상을 확인하였습니다.

### 5.2 기술적 제언

* 고성능 MySQL 어플리케이션 구성을 위해 JDBC URL에 `useServerPrepStmts=true` 및 `cachePrepStmts=true` 설정을 권장합니다.
* 긴 쿼리를 사용하는 경우 `prepStmtCacheSqlLimit` 값을 쿼리 길이에 맞춰 적절히 상향 조정해야 캐싱 혜택을 받을 수 있습니다.

---

## 6. 참고 자료

* [카카오페이 기술 블로그: PreparedStatement 동작 원리 분석](https://tech.kakaopay.com/post/how-preparedstatement-works-in-our-apps/)
* [MySQL Connector/J Reference: Performance Extensions](https://www.google.com/search?q=https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html)

