package gr.cytech.events.daos;

import io.quarkus.hibernate.orm.runtime.dialect.QuarkusPostgreSQL10Dialect;
import org.hibernate.type.InstantType;
import org.hibernate.type.LocalDateType;
import org.hibernate.type.StandardBasicTypes;

import java.sql.Types;

public class JsonPostgresDialect extends QuarkusPostgreSQL10Dialect {
    public JsonPostgresDialect() {
        super();
        this.registerHibernateType(Types.BIGINT, StandardBasicTypes.LONG.getName());
        this.registerHibernateType(Types.OTHER, StandardBasicTypes.STRING.getName());
        this.registerHibernateType(Types.DATE, LocalDateType.INSTANCE.getName());
        this.registerHibernateType(Types.TIMESTAMP_WITH_TIMEZONE, InstantType.INSTANCE.getName());
        this.registerHibernateType(Types.TIMESTAMP, InstantType.INSTANCE.getName());
    }
}
