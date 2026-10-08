package co.inter.piggies.testing;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Valida payloads contra os JSON Schemas da pasta {@code contracts/events} (contract-first).
 *
 * <pre>{@code
 * ContractSchemas.assertValid("pagamento-confirmado.v1.schema.json", json);
 * }</pre>
 */
public final class ContractSchemas {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private ContractSchemas() {
    }

    /** Erros de validação; vazio quando o JSON respeita o contrato. */
    public static Set<ValidationMessage> validate(String schemaFileName, String json) {
        try {
            String schema = Files.readString(locate(schemaFileName), StandardCharsets.UTF_8);
            JsonSchema jsonSchema = FACTORY.getSchema(schema);
            return jsonSchema.validate(json, InputFormat.JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void assertValid(String schemaFileName, String json) {
        Set<ValidationMessage> errors = validate(schemaFileName, json);
        if (!errors.isEmpty()) {
            throw new AssertionError("Payload viola o contrato " + schemaFileName + ": " + errors);
        }
    }

    /** Sobe pelos diretórios até achar {@code contracts/events/<arquivo>} (funciona de qualquer módulo). */
    static Path locate(String schemaFileName) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("contracts").resolve("events").resolve(schemaFileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Schema não encontrado em contracts/events: " + schemaFileName);
    }
}
