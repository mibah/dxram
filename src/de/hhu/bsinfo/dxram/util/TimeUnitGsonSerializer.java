package de.hhu.bsinfo.dxram.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import de.hhu.bsinfo.utils.unit.TimeUnit;

import java.lang.reflect.Type;

/**
 * Special Gson serializer/deserializer for a TimeUnit
 *
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 24.10.16
 */
public class TimeUnitGsonSerializer implements JsonDeserializer<TimeUnit>, JsonSerializer<TimeUnit> {
    @Override
    public TimeUnit deserialize(final JsonElement p_jsonElement, final Type p_type,
                                final JsonDeserializationContext p_jsonDeserializationContext) throws JsonParseException {

        JsonObject jsonObj = p_jsonElement.getAsJsonObject();
        long value = jsonObj.get("m_value").getAsLong();

        JsonElement unitElem = jsonObj.get("m_unit");
        String unit;
        if (unitElem == null) {
            unit = TimeUnit.MS;
        } else {
            unit = unitElem.getAsString();
        }

        return new TimeUnit(value, unit);
    }

    @Override
    public JsonElement serialize(final TimeUnit p_TimeUnit, final Type p_type,
                                 final JsonSerializationContext p_jsonSerializationContext) {

        JsonObject jsonObj = new JsonObject();

        // DXRAM's default time units are at least ms
        jsonObj.addProperty("m_value", p_TimeUnit.getMs());
        jsonObj.addProperty("m_unit", TimeUnit.MS);

        return jsonObj;
    }
}