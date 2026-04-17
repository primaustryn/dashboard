package com.shb.dashboard.service;

import com.shb.dashboard.dao.WidgetDefinitionDao;
import com.shb.dashboard.exception.WidgetNotFoundException;
import com.shb.dashboard.model.WidgetDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WidgetDefinitionService {

    private final WidgetDefinitionDao widgetDefinitionDao;

    public WidgetDefinitionService(WidgetDefinitionDao widgetDefinitionDao) {
        this.widgetDefinitionDao = widgetDefinitionDao;
    }

    public void register(WidgetDefinition def) {
        widgetDefinitionDao.insert(def);
    }

    public void update(String widgetId, WidgetDefinition def) {
        def.setWidgetId(widgetId);
        if (widgetDefinitionDao.update(def) == 0) throw new WidgetNotFoundException(widgetId);
    }

    public void deactivate(String widgetId) {
        if (widgetDefinitionDao.setActive(widgetId, false) == 0) throw new WidgetNotFoundException(widgetId);
    }

    public void activate(String widgetId) {
        if (widgetDefinitionDao.setActive(widgetId, true) == 0) throw new WidgetNotFoundException(widgetId);
    }

    public void remove(String widgetId) {
        if (widgetDefinitionDao.delete(widgetId) == 0) throw new WidgetNotFoundException(widgetId);
    }

    public List<WidgetDefinition> listAll() {
        return widgetDefinitionDao.findAll();
    }

    public List<WidgetDefinition> listActive() {
        return widgetDefinitionDao.findActive();
    }
}
