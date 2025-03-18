package main;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

public class ClientAgent extends Agent {
    private AID server;
    private boolean connected = false;
    private Logger logger = Logger.getLogger(getClass().getName());

    protected void setup() {
        logger.info("Клиент запущен. Используйте команды в терминале для управления:");
        logger.info("connect - подключиться к серверу");
        logger.info("start - начать распределение");
        logger.info("stop - закончить распределение");
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
                    connected = true; // Устанавливаем флаг подключения
                    logger.info("[Сервер] Подключен к серверу.");
                } else if (content.startsWith("DISTRIBUTION_RESULTS:")) {
                    String results = content.substring(21);
                    logger.info("[Сервер] Распределение товаров завершено. Результаты:\n" + results);
                } else if (content.startsWith("UNASSIGNED_GOODS:")) {
                    String unassignedGoods = content.substring(17);
                    logger.warning("[Сервер] Некоторые товары не удалось распределить: " + unassignedGoods);
                } else if (content.equals("PING")) {
                    // Ответ на пинг
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("PONG");
                    send(reply);
                } else if (content.startsWith("NOTIFICATION:")) {
                    // Уведомления от сервера
                    String notification = content.substring(13);
                    logger.info("[Сервер] " + notification);
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
                    } else if (input.equalsIgnoreCase("start")) {
                        sendCommand("start");
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
        logger.info("disconnect - отключиться от серверу");
        logger.info("status - проверить статус подключения");
        logger.info("start - запустить процесс распределения");
        logger.info("stop - остановить процесс распределения");
        logger.info("help - показать эту справку");
        logger.info("exit - выход");
    }
}