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

    /** Injects the DAO pair: definition persistence and audit trail recording. */
    public WidgetDefinitionService(WidgetDefinitionDao widgetDefinitionDao,
                                   WidgetAuditDao widgetAuditDao) {
        this.widgetDefinitionDao = widgetDefinitionDao;
        this.widgetAuditDao      = widgetAuditDao;
    }

    /**
     * Persists a new widget into WIDGET_MASTER, WIDGET_QUERY, and WIDGET_CONFIG,
     * then records a CREATE event in WIDGET_AUDIT.
     */
    public void register(WidgetDefinition def) {
        widgetDefinitionDao.insert(def);
        widgetAuditDao.record(def.getWidgetId(), "CREATE");
    }

    /**
     * Replaces the SQL chunks and config EAV rows of an existing widget.
     * The {@code widgetId} from the URL path overrides any value in the request body.
     * Throws {@link WidgetNotFoundException} if no WIDGET_MASTER row is found.
     * Records an UPDATE event in WIDGET_AUDIT on success.
     */
    public void update(String widgetId, WidgetDefinition def) {
        def.setWidgetId(widgetId);
        if (widgetDefinitionDao.update(def) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "UPDATE");
    }

    /**
     * Sets {@code is_active = false} on the widget, hiding it from dashboard queries
     * without deleting its definition.  Throws {@link WidgetNotFoundException} if not found.
     * Records a DEACTIVATE event in WIDGET_AUDIT on success.
     */
    public void deactivate(String widgetId) {
        if (widgetDefinitionDao.setActive(widgetId, false) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "DEACTIVATE");
    }

    /**
     * Sets {@code is_active = true} on the widget, making it visible on the dashboard.
     * Throws {@link WidgetNotFoundException} if not found.
     * Records an ACTIVATE event in WIDGET_AUDIT on success.
     */
    public void activate(String widgetId) {
        if (widgetDefinitionDao.setActive(widgetId, true) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "ACTIVATE");
    }

    /**
     * Permanently deletes the widget from WIDGET_MASTER and all related tables.
     * Throws {@link WidgetNotFoundException} if not found.
     * Records a DELETE event in WIDGET_AUDIT on success.
     */
    public void remove(String widgetId) {
        if (widgetDefinitionDao.delete(widgetId) == 0) throw new WidgetNotFoundException(widgetId);
        widgetAuditDao.record(widgetId, "DELETE");
    }

    /** Returns all registered widget definitions, active or inactive. */
    public List<WidgetDefinition> listAll() {
        return widgetDefinitionDao.findAll();
    }

    /** Returns only widget definitions with {@code is_active = true}. */
    public List<WidgetDefinition> listActive() {
        return widgetDefinitionDao.findActive();
    }
}
