package main;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Серверный агент, контролирующий процесс распределения товаров
 * и обеспечивающий подключение клиентов через терминал
 */
public class ServerAgent extends Agent {
    private List<AID> connectedClients = new ArrayList<>();
    private boolean processRunning = false;
    private Logger logger = Logger.getLogger(getClass().getName());
    private Map<String, AgentController> runningAgents = new HashMap<>();

    protected void setup() {
        logger.info("Сервер распределения товаров запущен и ожидает подключения клиентов");

        // Регистрация сервиса в DF (Directory Facilitator)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("goods-distribution");
        sd.setName("JADE-Distribution-Server");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            logger.info("Сервис зарегистрирован в DF");
        } catch (FIPAException fe) {
            logger.severe("Ошибка регистрации сервиса: " + fe.getMessage());
        }

        // Поведение для обработки подключений клиентов
        addBehaviour(new ClientConnectionBehaviour());
        addBehaviour(new ModelAgentMessageBehaviour());

        // Поведение для проверки статуса клиентов
        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                checkClientsStatus();
            }
        });
    }
    private class ModelAgentMessageBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = receive(template);
            if (msg != null) {
                String content = msg.getContent();
                if (content.startsWith("DISTRIBUTION_RESULTS:")) {
                    String results = content.substring(21);
                    notifyClients("Распределение товаров завершено. Результаты: " + results);
                }
            } else {
                block();
            }
        }
    }
    @Override
    protected void takeDown() {
        // Остановка всех агентов
        stopDistributionProcess();

        // Отмена регистрации сервиса
        try {
            DFService.deregister(this);
            logger.info("Сервис удален из DF");
        } catch (FIPAException fe) {
            logger.severe("Ошибка при удалении сервиса: " + fe.getMessage());
        }

        logger.info("Сервер остановлен");
    }

    /**
     * Поведение для обработки подключений клиентов
     */
    private class ClientConnectionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));

            if (msg != null) {
                AID sender = msg.getSender();
                String content = msg.getContent();

                if (content.equals("CONNECT")) {
                    // Запрос на подключение
                    if (!connectedClients.contains(sender)) {
                        connectedClients.add(sender);
                        logger.info("Клиент подключен: " + sender.getLocalName());

                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("CONNECTED");
                        send(reply);

                        if (connectedClients.size() == 1 && !processRunning) {
                            startDistributionProcess();
                        }
                    }
                } else if (content.equals("DISCONNECT")) {
                    // Запрос на отключение
                    connectedClients.remove(sender);
                    logger.info("Клиент отключен: " + sender.getLocalName());

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("DISCONNECTED");
                    send(reply);

                    if (connectedClients.isEmpty() && processRunning) {
                        stopDistributionProcess();
                    }
                } else if (content.startsWith("COMMAND:")) {
                    // Обработка команд от клиента
                    String command = content.substring(8).trim();
                    processClientCommand(command, sender);
                } else if (content.startsWith("DISTRIBUTION_RESULTS:")) {
                    // Обработка результатов распределения
                    String results = content.substring(21);
                    notifyClients("Распределение товаров завершено. Результаты: " + results);
                }
            } else {
                block();
            }
        }
    }

    /**
     * Проверка статуса подключения клиентов через пинг
     */
    private void checkClientsStatus() {
        List<AID> disconnectedClients = new ArrayList<>();

        // Проверка каждого клиента на доступность
        for (AID client : connectedClients) {
            ACLMessage ping = new ACLMessage(ACLMessage.QUERY_IF);
            ping.addReceiver(client);
            ping.setContent("PING");
            ping.setReplyWith("ping" + System.currentTimeMillis());

            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchInReplyTo(ping.getReplyWith())
            );

            send(ping);

            // Ждем ответа с таймаутом
            ACLMessage reply = blockingReceive(mt, 2000);

            if (reply == null) {
                // Нет ответа - клиент считается отключенным
                disconnectedClients.add(client);
                logger.warning("Клиент не отвечает и будет отключен: " + client.getLocalName());
            }
        }

        // Удаление отключенных клиентов
        for (AID client : disconnectedClients) {
            connectedClients.remove(client);
        }

        // Управление процессом в зависимости от наличия клиентов
        if (connectedClients.isEmpty() && processRunning) {
            stopDistributionProcess();
        } else if (!connectedClients.isEmpty() && !processRunning) {
            startDistributionProcess();
        }
    }

    /**
     * Запуск процесса распределения товаров
     */
    private void startDistributionProcess() {
        if (processRunning) {
            logger.info("Процесс распределения уже запущен");
            return;
        }

        if (connectedClients.isEmpty()) {
            logger.info("Невозможно запустить процесс - нет подключенных клиентов");
            return;
        }

        logger.info("Запуск процесса распределения товаров");

        // Создание агентов для процесса распределения
        try {
            ContainerController container = getContainerController();

            // Параметр - количество транспортных средств
            Object[] modelArgs = new Object[] { 3 }; // 3 транспортных средства

            // Создание основного агента-модели
            AgentController modelAgent = container.createNewAgent(
                    "ModelAgent",
                    "agents.ModelAgent",
                    modelArgs
            );
            modelAgent.start();
            runningAgents.put("ModelAgent", modelAgent);
            ACLMessage startMsg = new ACLMessage(ACLMessage.INFORM);
            startMsg.addReceiver(new AID("ModelAgent", AID.ISLOCALNAME));
            startMsg.setContent("START_DISTRIBUTION");
            send(startMsg);
            logger.info("Команда START_DISTRIBUTION отправлена ModelAgent");
            processRunning = true;

            // Уведомление клиентов о запуске процесса
            notifyClients("Процесс распределения товаров запущен");

        } catch (Exception e) {
            logger.severe("Ошибка при запуске процесса: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Остановка процесса распределения товаров
     */
    private void stopDistributionProcess() {
        if (!processRunning) {
            logger.info("Процесс распределения не запущен");
            return;
        }

        logger.info("Остановка процесса распределения товаров");

        // Завершение работы всех агентов
        for (Map.Entry<String, AgentController> entry : runningAgents.entrySet()) {
            try {
                entry.getValue().kill();
                logger.info("Агент " + entry.getKey() + " остановлен");
            } catch (Exception e) {
                logger.warning("Ошибка при остановке агента " + entry.getKey() + ": " + e.getMessage());
            }
        }

        runningAgents.clear();
        processRunning = false;

        // Уведомление клиентов об остановке процесса
        notifyClients("Процесс распределения товаров остановлен");
    }

    /**
     * Обработка команд от клиента
     */
    private void processClientCommand(String command, AID sender) {
        logger.info("Получена команда от клиента " + sender.getLocalName() + ": " + command);

        String response = "Команда получена";

        // Обработка основных команд
        if (command.equalsIgnoreCase("status")) {
            response = "Статус сервера: " +
                    (processRunning ? "Процесс запущен" : "Процесс остановлен") +
                    ", Подключено клиентов: " + connectedClients.size();
        } else if (command.equalsIgnoreCase("start")) {
            if (!processRunning) {
                startDistributionProcess();
                response = "Процесс распределения товаров запущен";
            } else {
                response = "Процесс уже запущен";
            }
        } else if (command.equalsIgnoreCase("stop")) {
            if (processRunning) {
                stopDistributionProcess();
                response = "Процесс распределения товаров остановлен";
            } else {
                response = "Процесс не запущен";
            }
        } else if (command.equalsIgnoreCase("restart")) {
            stopDistributionProcess();
            startDistributionProcess();
            response = "Процесс распределения товаров перезапущен";
        } else if (command.equalsIgnoreCase("help")) {
            response = "Доступные команды:\n" +
                    "status - проверить статус сервера\n" +
                    "start - запустить процесс распределения\n" +
                    "stop - остановить процесс распределения\n" +
                    "restart - перезапустить процесс\n" +
                    "clients - список подключенных клиентов\n" +
                    "help - показать это сообщение";
        } else if (command.equalsIgnoreCase("clients")) {
            StringBuilder sb = new StringBuilder("Подключенные клиенты:\n");
            for (AID client : connectedClients) {
                sb.append("- ").append(client.getLocalName()).append("\n");
            }
            response = sb.toString();
        }

        // Отправка ответа клиенту
        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
        reply.addReceiver(sender);
        reply.setContent("RESULT: " + response);
        send(reply);
    }

    /**
     * Уведомление всех подключенных клиентов
     */
    private void notifyClients(String message) {
        for (AID client : connectedClients) {
            ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
            notification.addReceiver(client);
            notification.setContent("NOTIFICATION: " + message);
            send(notification);
        }
    }
}