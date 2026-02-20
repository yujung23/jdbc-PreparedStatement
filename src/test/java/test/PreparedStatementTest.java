package test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PreparedStatementTest {

    private final String JDBC_URL = "jdbc:mysql://localhost:3306/sakila";
    private final String USER = "root";
    private final String PASS = "1234";
    
    // 쿼리
    private final String SQL = """
            SELECT a.first_name, a.last_name, c.name AS category, 
                   COUNT(r.rental_id) AS total_rentals, SUM(p.amount) AS total_revenue
            FROM actor a
            JOIN film_actor fa ON a.actor_id = fa.actor_id
            JOIN film f ON fa.film_id = f.film_id
            JOIN film_category fc ON f.film_id = fc.film_id
            JOIN category c ON fc.category_id = c.category_id
            JOIN inventory i ON f.film_id = i.film_id
            JOIN rental r ON i.inventory_id = r.inventory_id
            JOIN payment p ON r.rental_id = p.rental_id
            WHERE a.actor_id = ?
            GROUP BY a.actor_id, c.category_id
            """;

    @Test
    @Order(1)
    @DisplayName("Case 1: 기본 설정 (Client-side, No Cache)")
    void testCase1() throws Exception {
        runTest("?useServerPrepStmts=false&cachePrepStmts=false");
    }

    @Test
    @Order(2)
    @DisplayName("Case 2: 서버 사이드 활성화 (Server-side, No Cache)")
    void testCase2() throws Exception {
        runTest("?useServerPrepStmts=true&cachePrepStmts=false");
    }

    @Test
    @Order(3)
    @DisplayName("Case 3: 최적화 모드 (Server-side + Cache 활성화)")
    void testCase3() throws Exception {
        // prepStmtCacheSqlLimit: 캐싱할 쿼리의 최대 길이를 지정 (기본값이 짧을 수 있어 설정)
        runTest("?useServerPrepStmts=true&cachePrepStmts=true&prepStmtCacheSqlLimit=2048");
    }

    @Test
    @Order(4)
    @DisplayName("Case 4: 반쪽 최적화 (Client-side Cache만 활성화)")
    void testCase4() throws Exception {
        runTest("?useServerPrepStmts=false&cachePrepStmts=true");
    }

    /**
     * 공통 테스트 로직
     */
    private void runTest(String props) throws Exception {
        System.out.println("\n====================================================");
        System.out.println("설정값: " + props);
        
        try (Connection conn = DriverManager.getConnection(JDBC_URL + props, USER, PASS)) {
            
            // [Point 1] 구현체 확인 및 캐싱 여부 증명
            PreparedStatement stmt1 = conn.prepareStatement(SQL);
            String implementation = stmt1.getClass().getName();
            stmt1.close(); // 캐시 활성화 시 이 시점에 map에 저장됨

            PreparedStatement stmt2 = conn.prepareStatement(SQL);
            boolean isObjectCached = (stmt1 == stmt2);
            stmt2.close();

            System.out.println("1. 구현체 클래스: " + implementation);
            System.out.println("2. 자바 객체 재사용 여부: " + isObjectCached);

            // [Point 2] 성능 테스트 (반복 실행)
            int iterations = 10000; // 1만 번 수행
            long start = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                try (PreparedStatement stmt = conn.prepareStatement(SQL)) {
                    stmt.setLong(1, (i % 200) + 1);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) { /* 결과 소모 */ }
                    }
                }
            }
            long end = System.currentTimeMillis();
            System.out.println("3. " + iterations + "회 실행 시간: " + (end - start) + "ms");

            // [Point 3] MySQL 서버 상태 확인
            checkServerStatus(conn);
        }
    }

    private void checkServerStatus(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS LIKE 'Prepared_stmt_count'")) {
            if (rs.next()) {
                System.out.println("4. MySQL 서버 내 Prepared Statement 개수: " + rs.getString(2));
            }
        }
    }
}