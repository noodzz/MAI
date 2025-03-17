package model;

import com.google.gson.Gson;


/**
 * Вспомогательный класс для работы с сообщениями.
 * Содержит статические методы для сериализации и десериализации объектов в JSON.
 */
public class MessageUtils {
    private static final Gson gson = new Gson();

    /**
     * Конвертирует объект в JSON строку.
     *
     * @param object объект для конвертации
     * @return строка JSON
     */
    public static String convertToJson(Object object) {
        return gson.toJson(object);
    }
}