package agents;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.Good;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class VehicleAgent extends Agent {
    private List<Good> assignedGoods = new ArrayList<>();
    private int capacity; // Грузоподъемность автомобиля
    private Logger logger;

    public VehicleAgent() {
        this.capacity = 0; // Значение по умолчанию
    }
    public VehicleAgent(int capacity) {
        this.capacity = capacity;
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            this.capacity = (int) args[0]; // Получаем грузоподъемность из аргументов
        } else {
            this.capacity = 0; // Значение по умолчанию
        }
        logger = Logger.getLogger(this.getClass().getName());
        logger.info("VehicleAgent " + getLocalName() + " запущен.");
        addBehaviour(new AssignmentBehaviour());
        addBehaviour(new CapacityRequestBehaviour());
    }
    private class CapacityRequestBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // Ожидаем только сообщения с перформативом REQUEST и содержимым "GET_CAPACITY"
            MessageTemplate template = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchContent("GET_CAPACITY")
            );
            ACLMessage msg = receive(template);
            if (msg != null) {
                // Отправляем грузоподъемность
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(String.valueOf(capacity));
                send(reply);
            } else {
                block();
            }
        }
    }

    private class AssignmentBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // Ожидаем сообщения с перформативом REQUEST и JSON-данными (не "GET_CAPACITY")
            MessageTemplate template = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.not(
                            MessageTemplate.MatchContent("GET_CAPACITY")
                    )
            );
            ACLMessage msg = receive(template);
            if (msg != null) {
                List<Good> goods = parseGoods(msg.getContent());
                boolean isCompatible = checkCompatibility(goods);
                sendResponse(msg.getSender(), isCompatible);
            } else {
                block();
            }
        }

        private List<Good> parseGoods(String json) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<List<Good>>() {}.getType();
                return gson.fromJson(json, type);
            } catch (JsonSyntaxException e) {
                logger.severe("Ошибка парсинга JSON: " + json);
                return new ArrayList<>(); // Возвращаем пустой список вместо null
            }
        }

        private void sendResponse(AID sender, boolean isCompatible) {
            ACLMessage reply = new ACLMessage(isCompatible ? ACLMessage.CONFIRM : ACLMessage.REFUSE);
            reply.addReceiver(sender);
            reply.setContent(isCompatible ? "Принято" : "Несовместимость");
            send(reply);
        }

        private boolean checkCompatibility(List<Good> newGoods) {
            // Упрощенная проверка совместимости
            return newGoods.stream().allMatch(good ->
                    assignedGoods.stream().allMatch(good::isCompatibleWith)
            );
        }
    }
}