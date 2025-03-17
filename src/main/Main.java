package main;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class Main {
    public static void main(String[] args) {
        // Запуск платформы JADE
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "true"); // Включить графический интерфейс JADE

        // Создание главного контейнера
        AgentContainer mainContainer = runtime.createMainContainer(profile);

        try {
            // Создание и запуск агента VehicleAgent
            AgentController vehicleAgentController = mainContainer.createNewAgent(
                    "VehicleAgent1", // Имя агента
                    "agents.VehicleAgent", // Полное имя класса агента (с пакетом)
                    new Object[]{} // Аргументы для агента (если нужны)
            );
            vehicleAgentController.start(); // Запуск агента

            // Создание и запуск агента ModelAgent
            AgentController modelAgentController = mainContainer.createNewAgent(
                    "ModelAgent1", // Имя агента
                    "agents.ModelAgent", // Полное имя класса агента (с пакетом)
                    new Object[]{} // Аргументы для агента
            );
            modelAgentController.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}