package agents;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.Good;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class VehicleAgent extends Agent {
    private List<Good> assignedGoods = new ArrayList<>();
    private Logger logger;

    @Override
    protected void setup() {
        logger = Logger.getLogger(this.getClass().getName());
        logger.info("VehicleAgent " + getLocalName() + " запущен.");
        addBehaviour(new AssignmentBehaviour());
    }

    private class AssignmentBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            if (msg != null) {
                List<Good> goods = parseGoods(msg.getContent());
                boolean isCompatible = checkCompatibility(goods);
                sendResponse(msg.getSender(), isCompatible);
            } else {
                block();
            }
        }

        private List<Good> parseGoods(String json) {
            // Парсинг JSON в список товаров
            return new Gson().fromJson(json, new TypeToken<List<Good>>(){}.getType());
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