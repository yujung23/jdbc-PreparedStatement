
# MySQL PreparedStatement 동작 분석 및 성능 실험

MySQL Connector/J 드라이버 옵션 조합에 따른 `PreparedStatement`의 실제 구현체 변화와 성능 차이를 실측하고 검증하는 프로젝트입니다.

---

## 1. 개요

대부분의 라이브러리에서 사용하는 `PreparedStatement`는 설정에 따라 **Client-side** 혹은 **Server-side**로 동작합니다. 본 프로젝트는 이 차이가 실제 Java 객체와 DB 서버 리소스에 미치는 영향을 분석합니다.

---

## 2. 실험 시나리오 (4 Cases)

| Case | `useServerPrepStmts` | `cachePrepStmts` | 동작 특징 |
| --- | --- | --- | --- |
| **1** | `false` | `false` | **기본값.** 클라이언트에서 문자열 치환 후 전송 (Text Protocol) |
| **2** | `true` | `false` | 서버에서 실행 계획 생성. 단, 캐시 미사용으로 매번 Prepare 발생 |
| **3** | **`true`** | **`true`** | **최적 환경.** 서버 실행 계획 재사용 및 드라이버 객체 캐싱 |
| **4** | `false` | `true` | 클라이언트 사이드에서 생성된 객체만 드라이버에서 캐싱 |

---

## 3. 실험 환경

* **언어**: Java 17
* **DB**: MySQL 8.4.0
* **드라이버**: MySQL Connector/J 8.4.0
* **대상 데이터**: Sakila Sample DB

---

## 4. 핵심 검증 로직 (`Main.java`)

### 4.1 구현체 클래스 확인

설정에 따라 드라이버가 내부적으로 사용하는 클래스가 동적으로 변경되는지 확인합니다.

```java
// 옵션에 따라 ClientPreparedStatement 또는 ServerPreparedStatement 생성
System.out.println("현재 사용 중인 구현체: " + testStmt.getClass().getName());

```

### 4.2 객체 캐싱(재사용) 여부 검증

`cachePrepStmts` 옵션 활성화 시, 동일한 SQL에 대해 기존 객체를 재사용하여 힙 메모리 부하를 줄이는지 확인합니다.

```java
// 동일 SQL에 대해 주소값(==) 비교
System.out.println("두 객체가 동일한가(캐싱 여부): " + (testStmt == testStmt2));

```

### 4.3 서버 사이드 실행 계획 모니터링

실제 MySQL 서버 엔진에 등록된 Prepared Statement 개수를 추적하여 서버 사이드 Prepare 동작을 물리적으로 증명합니다.

```java
// SHOW GLOBAL STATUS LIKE 'Prepared_stmt_count' 쿼리 실행
System.out.println("MySQL 서버 내 Prepared Statement 개수: " + rs.getString(2));

```

---

## 5. 성능 측정 전략

* **반복 실행**: 동일한 복잡한 JOIN 쿼리를 **20,000회** 호출하여 소요 시간(ms) 측정
* **복잡한 쿼리**: SQL 파싱 비용이 높은 다중 테이블 JOIN 및 집계 함수 사용

```java
for (int i = 0; i < 20000; i++) {
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setLong(1, (i % 200) + 1);
        stmt.executeQuery();
    }
}

```

---

## 6. 결론 및 제언

1. **Case 3 (Server-side + Cache)** 환경에서 실행 계획 재사용과 객체 캐싱이 결합되어 가장 뛰어난 성능을 보입니다.
2. 단순한 쿼리보다 **복잡한 JOIN 쿼리**일수록 서버 사이드 Prepare의 효율이 극대화됩니다.
3. 설정 옵션 하나가 네트워크 프로토콜(Text vs Binary)부터 서버 리소스 사용량까지 변경함을 확인할 수 있습니다.

---

## 7. 참고 자료

* [카카오페이 기술 블로그: PreparedStatement 동작 원리 분석](https://tech.kakaopay.com/post/how-preparedstatement-works-in-our-apps/)
* [MySQL Connector/J 8.4 Reference](https://www.google.com/search?q=https://dev.mysql.com/doc/connector-j/8.4/en/connector-j-connp-props-performance-extensions.html)

