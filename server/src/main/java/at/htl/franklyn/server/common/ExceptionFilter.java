package at.htl.franklyn.server.common;

import jakarta.ws.rs.WebApplicationException;

import java.util.function.Predicate;

public class ExceptionFilter {
    public static final Predicate<? super Throwable> NO_WEBAPP =
            throwable -> !(throwable instanceof WebApplicationException);
}
