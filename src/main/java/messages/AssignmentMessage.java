package messages;


import com.google.gson.annotations.SerializedName;
import model.Good;

import java.util.List;

public class AssignmentMessage {
    @SerializedName("messageType")
    private String messageType;

    @SerializedName("payload")
    private List<Good> payload;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("vehicleId")
    private String vehicleId;


    public AssignmentMessage(String messageType, List<Good> payload, String vehicleId) {
        if (messageType == null || payload == null || vehicleId == null) {
            throw new IllegalArgumentException("Поля messageType, payload и vehicleId не могут быть null");
        }
        this.messageType = messageType;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
        this.vehicleId = vehicleId;
    }
    @Override
    public String toString() {
        return "AssignmentMessage{" +
                "messageType='" + messageType + '\'' +
                ", payload=" + payload +
                ", timestamp=" + timestamp +
                ", vehicleId='" + vehicleId + '\'' +
                '}';
    }
    // Геттеры
    public String getMessageType() { return messageType; }
    public List<Good> getPayload() { return payload; }
    public long getTimestamp() { return timestamp; }
    public String getVehicleId() { return vehicleId; }

}

