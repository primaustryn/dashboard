package com.shb.dashboard.service;

import com.shb.dashboard.dao.WidgetAuditDao;
import com.shb.dashboard.dao.WidgetDefinitionDao;
import com.shb.dashboard.exception.WidgetNotFoundException;
import com.shb.dashboard.model.WidgetDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WidgetDefinitionService {

    private final WidgetDefinitionDao widgetDefinitionDao;
    private final WidgetAuditDao widgetAuditDao;

    public WidgetDefinitionService(WidgetDefinitionDao widgetDefinitionDao,
                                   WidgetAuditDao widgetAuditDao) {
        this.widgetDefinitionDao = widgetDefinitionDao;
        this.widgetAuditDao      = widgetAuditDao;
    }

    public void register(WidgetDefinition def) {
        widgetDefinitionDao.insert(def);
        widgetAuditDao.record(def.getWidgetId(), "CREATE");
    }

    public void update(String widgetId, WidgetDefinition def) {
        def.setWidgetId(widgetId);
        if (widgetDefinitionDao.update(def) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "UPDATE");
    }

    public void deactivate(String widgetId) {
        if (widgetDefinitionDao.setActive(widgetId, false) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "DEACTIVATE");
    }

    public void activate(String widgetId) {
        if (widgetDefinitionDao.setActive(widgetId, true) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "ACTIVATE");
    }

    public void remove(String widgetId) {
        if (widgetDefinitionDao.delete(widgetId) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "DELETE");
    }

    public List<WidgetDefinition> listAll() {
        return widgetDefinitionDao.findAll();
    }

    public List<WidgetDefinition> listActive() {
        return widgetDefinitionDao.findActive();
    }
}
