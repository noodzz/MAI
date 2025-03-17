package agents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SequentialBehaviour;
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

/**
 * Агент модели, управляемый ServerAgent. Занимается только распределением товаров.
 */
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
        for (int i = 0; i < numVehicles; i++) {
            String vehicleName = "Vehicle-" + i;
            try {
                AgentController ac = getContainerController().createNewAgent(
                        vehicleName,
                        "agents.VehicleAgent",
                        null
                );
                ac.start();
                vehicleAgents.put(vehicleName, new AID(vehicleName, AID.ISLOCALNAME));
                logger.info("Создан транспортный агент: " + vehicleName);
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
            JSONObject jsonObject = (JSONObject) parser.parse(new FileReader("C:\\Users\\silez\\IdeaProjects\\MAI\\src\\main\\resources\\goods.json"));
            JSONArray goodsArray = (JSONArray) jsonObject.get("goods");
            for (Object obj : goodsArray) {
                JSONObject goodJson = (JSONObject) obj;
                String id = (String) goodJson.get("id");
                int weight = ((Long) goodJson.get("weight")).intValue();
                goods.add(new Good(id, weight, (JSONArray) goodJson.get("incompatibilities")));
            }
            logger.info("Загружено товаров: " + goods.size());
        } catch (Exception e) {
            logger.severe("Ошибка загрузки товаров: " + e.getMessage());
        }
    }

    private void distributeGoods() {
        DistributionAlgorithm algorithm = new DistributionAlgorithm(this, goods, vehicleAgents, logger);
        Map<String, List<Good>> distribution = algorithm.distributeGoods();
        for (Good good : goods) {
            if (!isGoodAssignedToVehicle(good, distribution)) {
                // Если товар не был назначен, создаем новый транспорт для него
                createNewVehicleAgent(good);
            }
        }
        sendAssignments(distribution);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        ACLMessage completionMsg = new ACLMessage(ACLMessage.INFORM);
        completionMsg.addReceiver(serverAgent);
        completionMsg.setContent("DISTRIBUTION_COMPLETE");
        send(completionMsg);

        ACLMessage distributionMsg = new ACLMessage(ACLMessage.INFORM);
        distributionMsg.addReceiver(serverAgent);
        distributionMsg.setContent("DISTRIBUTION_RESULTS:" + gson.toJson(distribution));
        send(distributionMsg);

        logger.info("Распределение завершено. Сервер уведомлен.");
        saveResultsToJson(distribution);
    }


    private void sendAssignments(Map<String, List<Good>> distribution) {
        distribution.forEach((vehicleName, goods) -> {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(vehicleName, AID.ISLOCALNAME));
            msg.setContent(new Gson().toJson(goods));
            send(msg);
        });
    }
    public void saveResultsToJson(Map<String, List<Good>> distribution) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String fileName = "distribution_results.json";

        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(distribution, writer);
            System.out.println("Результаты сохранены в файл: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createNewVehicleAgent(Good good) {
        String newVehicleName = "Vehicle-" + (vehicleAgents.size() + 1);
        try {
            // Создаем нового транспортного агента
            AgentController newVehicle = getContainerController()
                    .createNewAgent(newVehicleName, "agents.VehicleAgent", new Object[]{});
            newVehicle.start();

            // Добавляем новый транспорт в vehicleAgents
            vehicleAgents.put(newVehicleName, new AID(newVehicleName, AID.ISLOCALNAME));
            logger.info("Создан новый транспортный агент: " + newVehicleName);

            // Создаем структуру распределения и передаем товар новому агенту
            Map<String, List<Good>> newDistribution = new HashMap<>();
            List<Good> goodsList = new ArrayList<>();
            goodsList.add(good);  // Передаем объект товара в новый транспорт
            newDistribution.put(newVehicleName, goodsList);

            sendAssignments(newDistribution);
        } catch (Exception e) {
            logger.severe("Ошибка при создании нового VehicleAgent: " + e.getMessage());
        }
    }

    private boolean isGoodAssignedToVehicle(Good good, Map<String, List<Good>> distribution) {
        for (List<Good> vehicleGoods : distribution.values()) {
            if (vehicleGoods.contains(good)) {
                return true;
            }
        }
        return false;
    }



}