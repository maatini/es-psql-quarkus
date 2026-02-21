package space.maatini.eventsourcing.exception;

import com.networknt.schema.ValidationMessage;
import java.util.Set;

public class JsonSchemaValidationException extends RuntimeException {
    
    private final Set<ValidationMessage> validationMessages;

    public JsonSchemaValidationException(String message, Set<ValidationMessage> validationMessages) {
        super(message);
        this.validationMessages = validationMessages;
    }

    public Set<ValidationMessage> getValidationMessages() {
        return validationMessages;
    }
}
