package org.elestya.plugin.sources;

public record ActionResult(boolean value, String message) {

    public static ActionResult success(String message){
        return new ActionResult(true, message);
    }

    public static ActionResult failure(String message){
        return new ActionResult(false, message);
    }

}
