package state;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {
	public static void main(String[] args) throws Exception {
		
		// 실험하고 싶은 케이스를 주석 해제하며 실행하세요.
		// Case 1: 기본 (Client side)
//		String properties = "?useServerPrepStmts=false&cachePrepStmts=false";
		
		// Case 2: 서버 사이드 활성화 (Server side)
		 String properties = "?useServerPrepStmts=true&cachePrepStmts=false";
		
		// Case 3: 서버 사이드 + 캐시 활성화 (Best Performance)
//		String properties = "?useServerPrepStmts=true&cachePrepStmts=true";
		
		// Case 4: 서버 사이드 비활성화 +
//		String properties = "?useServerPrepStmts=false&cachePrepStmts=true";
		
		String jdbcUrl = "jdbc:mysql://localhost:3306/sakila";
		String id = "root";
		String password = "1234";

		try (Connection connection = DriverManager.getConnection(jdbcUrl + properties, id, password)) {

			// [추가 포인트 1] 어떤 구현체 클래스가 생성되는지 확인
			
//			PreparedStatement testStmt = connection.prepareStatement("SELECT * FROM actor WHERE actor_id = ?");
			PreparedStatement testStmt = connection.prepareStatement("""
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
             """);
			System.out.println("현재 사용 중인 구현체: " + testStmt.getClass().getName());
			testStmt.close();
			
			// [추가 포인트 2] 객체 캐싱(재사용) 여부 확인 (블로그 핵심 내용)
//			PreparedStatement testStmt2 = connection.prepareStatement("SELECT * FROM actor WHERE actor_id = ?");
			PreparedStatement testStmt2 = connection.prepareStatement("""
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
             """);
			testStmt2.close();
			
			System.out.println("두 객체가 동일한가(캐싱 여부): " + (testStmt == testStmt2));

			
			// 성능 테스트 시작
			long start = System.currentTimeMillis();
			for (int i = 0; i < 20000; i++) {
				try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM actor WHERE actor_id = ?")) {
//					stmt.setLong(1, i + 1);
					stmt.setLong(1, (i % 200) + 1);
					try (ResultSet rs = stmt.executeQuery()) {
						// 결과 처리 생략
					}
				}
			}
			long end = System.currentTimeMillis();

			System.out.println("설정값: " + properties);
			System.out.println("실행시간: " + (end - start) + "ms");

			
			// [추가 포인트 3] MySQL 서버에 Prepared Statement가 몇 개 생성되었는지 확인
			checkServerStatus(connection);
		}
	}

	private static void checkServerStatus(Connection conn) throws Exception {
	    try (Statement st = conn.createStatement();
	         ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS LIKE 'Prepared_stmt_count'")) {
	        if (rs.next()) {
	            System.out.println("MySQL 서버 내 Prepared Statement 개수: " + rs.getString(2));
	        }
	    }
	}
}