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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Клиентский агент для подключения к серверу распределения товаров
 * через терминал
 */
public class ClientAgent extends Agent {
    private AID server;
    private boolean connected = false;
    private Logger logger = Logger.getLogger(getClass().getName());

    protected void setup() {
        logger.info("Клиент запущен. Используйте команды в терминале для управления:");
        logger.info("connect - подключиться к серверу");
        logger.info("disconnect - отключиться от сервера");
        logger.info("status - проверить статус");
        logger.info("help - список команд");
        logger.info("exit - выход");

        // Поиск сервера
        addBehaviour(new FindServerBehaviour());

        // Обработка сообщений от сервера
        addBehaviour(new ServerMessageBehaviour());

        // Обработка пользовательского ввода
        addBehaviour(new UserInputBehaviour(this, 100));
    }

    @Override
    protected void takeDown() {
        // Отключение от сервера при завершении
        if (connected && server != null) {
            sendDisconnectRequest();
        }

        logger.info("Клиент остановлен");
    }

    /**
     * Поведение для поиска сервера
     */
    private class FindServerBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("goods-distribution");
            template.addServices(sd);

            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result.length > 0) {
                    server = result[0].getName();
                    logger.info("Найден сервер: " + server.getLocalName());
                    logger.info("Используйте команду 'connect' для подключения");
                } else {
                    logger.warning("Сервер не найден. Убедитесь, что сервер запущен");
                }
            } catch (FIPAException fe) {
                logger.severe("Ошибка при поиске сервера: " + fe.getMessage());
            }
        }
    }

    /**
     * Поведение для обработки сообщений от сервера
     */
    private class ServerMessageBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();

            if (msg != null) {
                String content = msg.getContent();

                if (content.equals("CONNECTED")) {
                    connected = true;
                    logger.info("Подключен к серверу успешно");
                } else if (content.equals("DISCONNECTED")) {
                    connected = false;
                    logger.info("Отключен от сервера");
                } else if (content.equals("PING")) {
                    // Ответ на пинг
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("PONG");
                    send(reply);
                } else if (content.startsWith("NOTIFICATION:")) {
                    String notification = content.substring(13);
                    logger.info("[Сервер] " + content.substring(13));
                    if (notification.contains("{")) { // Если это JSON
                        logger.info("[Сервер] Распределение товаров завершено. Результаты:\n" + notification);
                    } else {
                        logger.info("[Сервер] " + notification);
                    }
                } else if (content.startsWith("RESULT:")) {
                    // Результат выполнения команды
                    logger.info("[Результат] " + content.substring(8));
                }
            } else {
                block();
            }
        }
    }

    /**
     * Поведение для обработки пользовательского ввода
     */
    private class UserInputBehaviour extends TickerBehaviour {
        private BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        public UserInputBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            try {
                if (reader.ready()) {
                    String input = reader.readLine().trim();

                    if (input.isEmpty()) {
                        return;
                    }

                    if (input.equalsIgnoreCase("exit")) {
                        myAgent.doDelete();
                    } else if (input.equalsIgnoreCase("connect")) {
                        sendConnectRequest();
                    } else if (input.equalsIgnoreCase("disconnect")) {
                        sendDisconnectRequest();
                    } else if (input.equalsIgnoreCase("status")) {
                        logger.info("Статус: " + (connected ? "Подключен" : "Отключен"));
                        if (server != null) {
                            logger.info("Сервер: " + server.getLocalName());
                        } else {
                            logger.info("Сервер не найден");
                        }
                    } else if (input.equalsIgnoreCase("help")) {
                        showHelp();
                    } else {
                        // Отправка команды на сервер
                        sendCommand(input);
                    }
                }
            } catch (IOException e) {
                logger.severe("Ошибка при чтении ввода: " + e.getMessage());
            }
        }
    }

    /**
     * Отправка запроса на подключение
     */
    private void sendConnectRequest() {
        if (server == null) {
            addBehaviour(new FindServerBehaviour());
            logger.info("Сервер не найден. Выполняется поиск...");
            return;
        }

        if (connected) {
            logger.info("Уже подключен к серверу");
            return;
        }

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(server);
        msg.setContent("CONNECT");
        send(msg);
        logger.info("Отправлен запрос на подключение к серверу");
    }

    /**
     * Отправка запроса на отключение
     */
    private void sendDisconnectRequest() {
        if (server == null || !connected) {
            logger.info("Не подключен к серверу");
            return;
        }

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(server);
        msg.setContent("DISCONNECT");
        send(msg);
        logger.info("Отправлен запрос на отключение от сервера");
    }

    /**
     * Отправка команды на сервер
     */
    private void sendCommand(String command) {
        if (server == null || !connected) {
            logger.info("Не подключен к серверу. Используйте 'connect' для подключения");
            return;
        }

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(server);
        msg.setContent("COMMAND:" + command);
        send(msg);
        logger.info("Команда отправлена: " + command);
    }

    /**
     * Вывод справки
     */
    private void showHelp() {
        logger.info("=== Список команд ===");
        logger.info("connect - подключиться к серверу");
        logger.info("disconnect - отключиться от сервера");
        logger.info("status - проверить статус подключения");
        logger.info("restart - перезапустить процесс");
        logger.info("clients - список подключенных клиентов");
        logger.info("help - показать эту справку");
        logger.info("exit - выход");
    }
}