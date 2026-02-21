package space.maatini.eventsourcing.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.enterprise.inject.Stereotype;

/**
 * Marks a class as a Command Handler.
 */
@Stereotype
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HandlesCommand {
    /**
     * The type of the command that this handler processes.
     */
    Class<?> value();
}
