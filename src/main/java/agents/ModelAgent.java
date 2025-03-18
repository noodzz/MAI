package agents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import model.DistributionAlgorithm;
import model.Good;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ModelAgent extends Agent {
    private List<Good> goods;
    private Map<String, AID> vehicleAgents;
    private Logger logger;
    private AID serverAgent;
    private int numVehicles;


    @Override
    protected void setup() {
        logger = Logger.getLogger(this.getClass().getName());
        goods = new ArrayList<>();
        vehicleAgents = new HashMap<>();
        serverAgent = findServerAgent();

        // Регистрация в DF
        registerAsModelAgent();
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                numVehicles = Integer.parseInt(args[0].toString());
            } catch (NumberFormatException e) {
                logger.severe("Ошибка парсинга аргумента: " + e.getMessage());
                numVehicles = 3; // Значение по умолчанию
            }
        } else {
            numVehicles = 3;
        }
        logger.info("Количество транспортных средств: " + numVehicles);

        // Создание транспортных агентов
        createVehicleAgents();
        // Ожидание команд от ServerAgent
        addBehaviour(new ServerCommandBehaviour());
        logger.info("ModelAgent готов к работе.");
    }

    private void createVehicleAgents() {
        int[] capacities = {55, 60, 65};
        for (int i = 0; i < numVehicles; i++) {
            String vehicleName = "Vehicle-" + i;
            try {
                Object[] args = new Object[] { capacities[i] }; // Передаем грузоподъемность
                AgentController ac = getContainerController().createNewAgent(
                        vehicleName,
                        "agents.VehicleAgent",
                        args
                );
                ac.start();
                vehicleAgents.put(vehicleName, new AID(vehicleName, AID.ISLOCALNAME));
                logger.info("Создан транспортный агент: " + vehicleName + ", грузоподъемность: " + capacities[i] + " кг.");
            } catch (Exception e) {
                logger.severe("Ошибка создания агента " + vehicleName + ": " + e.getMessage());
            }
        }
    }

    private AID findServerAgent() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("goods-distribution");
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            return result[0].getName();
        } catch (FIPAException e) {
            logger.severe("ServerAgent не найден: " + e.getMessage());
            doDelete();
            return null;
        }
    }

    private void registerAsModelAgent() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("model-agent");
        sd.setName("ModelAgent");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            logger.severe("Ошибка регистрации: " + e.getMessage());
        }
    }

    private class ServerCommandBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.MatchSender(serverAgent);
            ACLMessage msg = receive(template);
            if (msg != null) {
                switch (msg.getContent()) {
                    case "START_DISTRIBUTION":
                        loadGoodsFromJson();
                        distributeGoods();
                        break;
                    case "STOP_DISTRIBUTION":
                        doDelete();
                        break;
                }
            } else {
                block();
            }
        }
    }

    private void loadGoodsFromJson() {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(new FileReader("src/main/resources/goods.json"));
            JSONArray goodsArray = (JSONArray) jsonObject.get("goods");
            for (Object obj : goodsArray) {
                JSONObject goodJson = (JSONObject) obj;
                String id = (String) goodJson.get("id");
                int weight = ((Long) goodJson.get("weight")).intValue();
                goods.add(new Good(id, weight, (JSONArray) goodJson.get("incompatibilities")));
            }
            logger.info("Загружено товаров: " + goods.size());
            ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
            notification.addReceiver(serverAgent);
            notification.setContent("NOTIFICATION: Загружено " + goods.size() + " товаров.");
            send(notification);
        } catch (Exception e) {
            logger.severe("Ошибка загрузки товаров: " + e.getMessage());
        }
    }

    private void distributeGoods() {
        Map<String, Integer> vehicleCapacities = getVehicleCapacities();
        DistributionAlgorithm algorithm = new DistributionAlgorithm(goods, vehicleAgents, vehicleCapacities, logger, this, serverAgent);
        Map<String, List<Good>> distribution = algorithm.distributeGoods();
        ACLMessage startNotification = new ACLMessage(ACLMessage.INFORM);
        startNotification.addReceiver(serverAgent);
        startNotification.setContent("NOTIFICATION: Начало распределения товаров.");
        send(startNotification);

        sendAssignments(distribution);

        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();

        ACLMessage distributionMsg = new ACLMessage(ACLMessage.INFORM);
        distributionMsg.addReceiver(serverAgent);
        distributionMsg.setContent("DISTRIBUTION_RESULTS:" + gson.toJson(distribution));
        send(distributionMsg);

        List<Good> unassignedGoods = algorithm.getUnassignedGoods();
        if (!unassignedGoods.isEmpty()) {
            ACLMessage unassignedMsg = new ACLMessage(ACLMessage.INFORM);
            unassignedMsg.addReceiver(serverAgent);
            unassignedMsg.setContent("UNASSIGNED_GOODS:" + gson.toJson(unassignedGoods));
            send(unassignedMsg);
            logger.warning("Некоторые товары не удалось распределить: " + unassignedGoods);
        }

        logger.info("Распределение завершено. Сервер уведомлен.");
        saveResultsToJson(distribution);
        ACLMessage endNotification = new ACLMessage(ACLMessage.INFORM);
        endNotification.addReceiver(serverAgent);
        endNotification.setContent("NOTIFICATION: Распределение товаров завершено.");
        send(endNotification);
    }


    private void sendAssignments(Map<String, List<Good>> distribution) {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation() // Добавьте настройку
                .create();

        distribution.forEach((vehicleName, goods) -> {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(vehicleName, AID.ISLOCALNAME));
            msg.setContent(gson.toJson(goods)); // Теперь сериализация с учетом @Expose
            send(msg);
        });
    }
    public void saveResultsToJson(Map<String, List<Good>> distribution) {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();

        String fileName = "distribution_results.json";

        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(distribution, writer);
            System.out.println("Результаты сохранены в файл: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private boolean isGoodFullyAssigned(Good good, Map<String, List<Good>> distribution) {
        String normalizedId = good.normalizeId(good.getId());
        boolean isPart = good.getId().matches(".*_part\\d+$");

        // Если это часть, проверяем её наличие в распределении
        if (isPart) {
            return distribution.values().stream()
                    .flatMap(List::stream)
                    .anyMatch(g -> g.getId().equals(good.getId()));
        }

        // Если это целый товар, проверяем все его части
        List<Good> allParts = goods.stream()
                .filter(g -> g.normalizeId(g.getId()).equals(normalizedId))
                .collect(Collectors.toList());

        long assignedParts = distribution.values().stream()
                .flatMap(List::stream)
                .filter(g -> g.normalizeId(g.getId()).equals(normalizedId))
                .count();

        return assignedParts == allParts.size();
    }
    private Map<String, Integer> getVehicleCapacities() {
        Map<String, Integer> capacities = new HashMap<>();
        for (Map.Entry<String, AID> entry : vehicleAgents.entrySet()) {
            String vehicleName = entry.getKey();
            AID vehicleAID = entry.getValue();

            // Отправляем запрос на получение грузоподъемности
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(vehicleAID);
            msg.setContent("GET_CAPACITY");
            send(msg);

            // Ждем ответа
            ACLMessage reply = blockingReceive(MessageTemplate.MatchSender(vehicleAID));
            if (reply != null && reply.getPerformative() == ACLMessage.INFORM) {
                int capacity = Integer.parseInt(reply.getContent());
                capacities.put(vehicleName, capacity);
                logger.info("Грузоподъемность " + vehicleName + ": " + capacity + " кг.");
            } else {
                logger.warning("Не удалось получить грузоподъемность от " + vehicleName);
            }
        }
        return capacities;
    }
}