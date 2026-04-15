package com.team.rag.agent.skill;

import java.util.Map;

public interface AgentSkill {

    String name();

    String description();

    boolean destructive();

    Map<String, Object> inputSchema();

    Object execute(Map<String, Object> arguments);
}
