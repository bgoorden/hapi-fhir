package ca.uhn.fhir.jpa.migrate.taskdef;

import ca.uhn.fhir.jpa.embedded.PostgresEmbeddedDatabase;
import ca.uhn.fhir.jpa.migrate.DriverTypeEnum;
import ca.uhn.fhir.jpa.migrate.HapiMigrator;
import ca.uhn.fhir.jpa.migrate.SchemaMigrator;
import ca.uhn.fhir.util.VersionEnum;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class MigrateColumBlobTypeToBinaryTypeTaskTest {

	private static PostgresEmbeddedDatabase ourPostgresEmbeddedDatabase;

	@BeforeAll
	public static void beforeAll(){
		ourPostgresEmbeddedDatabase = new PostgresEmbeddedDatabase();
	}

	private HapiMigrator myMigrator;

	@BeforeEach
	public void beforeEach(){
		myMigrator = new HapiMigrator(SchemaMigrator.HAPI_FHIR_MIGRATION_TABLENAME, ourPostgresEmbeddedDatabase.getDataSource(), DriverTypeEnum.POSTGRES_9_4);
		myMigrator.createMigrationTableIfRequired();
	}

	@Test
	public void testMigrationTask_OidValueIsCopiedIntoBytea(){
		// given
		final String expectedString ="Hello world!";

		ourPostgresEmbeddedDatabase.executeSqlAsBatch(List.of(
			"create table HFJ_STORAGE_WITH_BLOB (BLOB_DATA oid, STORAGE_CONTENT_BIN bytea)",
			"select lo_create(1234)",     // create empty LOB with id 1234
			"select lo_put(1234, 0, '\\x48656c6c6f20776f726c6421')",  // insert data (Hello world!) in the LOB with id 1234
			"insert into HFJ_STORAGE_WITH_BLOB (BLOB_DATA) values (1234)" // assign LOB id to colum
		));

		// when
		BaseTask task = new MigrateColumBlobTypeToBinaryTypeTask(
			VersionEnum.V7_2_0.toString(),
			"1",
			"HFJ_STORAGE_WITH_BLOB",
			"BLOB_DATA",         // colum of oid type
			"STORAGE_CONTENT_BIN"                 // colum of bytea type
		);

		myMigrator.addTask(task);
		myMigrator.migrate();

		// then
		List<Map<String, Object>> rows = ourPostgresEmbeddedDatabase.query("select * from HFJ_STORAGE_WITH_BLOB");

		assertThat(rows, hasSize(1));

		Map<String, Object> stringObjectMap = rows.get(0);

		String storedContent = convertToString(stringObjectMap, "storage_content_bin");

		assertThat(storedContent, equalTo(expectedString));

	}

	public String convertToString(Map<String, Object> theMap, String theKey){
		Object o = theMap.get(theKey);

		byte[] convertedOidToBytea = ((byte[])o);
		return new String(convertedOidToBytea);
	}

	@AfterEach
	public void afterEach(){
		ourPostgresEmbeddedDatabase.clearDatabase();
	}

	@AfterAll
	public static void afterAll(){

		ourPostgresEmbeddedDatabase.stop();

	}

}
