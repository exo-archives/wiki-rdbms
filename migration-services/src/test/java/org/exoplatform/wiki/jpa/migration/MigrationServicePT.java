package org.exoplatform.wiki.jpa.migration;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.bench.WikiDataInjector;
import org.exoplatform.wiki.mow.api.DraftPage;
import org.exoplatform.wiki.mow.api.Page;
import org.exoplatform.wiki.mow.api.Wiki;
import org.exoplatform.wiki.service.WikiService;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

/**
 *
 */
public class MigrationServicePT extends MigrationITSetup {

  private static final Log LOG = ExoLogger.getLogger(MigrationServicePT.class);

  @Override
  public void setUp() {
    super.setUp();

    WikiService wikiService = PortalContainer.getInstance().getComponentInstanceOfType(WikiService.class);

    startSessionAs("user1");

    // Portal Wikis
    WikiDataInjector portalWikiDataInjector = PortalContainer.getInstance().getComponentInstanceOfType(WikiDataInjector.class);
    HashMap<String, String> portalInjectorParams = new HashMap<>();
    portalInjectorParams.put("type", "data");
    portalInjectorParams.put("q", "1,10,10");
    portalInjectorParams.put("pre", "Public%20Wiki%20A,Public%20Wiki%20AB,Public%20Wiki%20ABC");
    portalInjectorParams.put("wo", "intranet");
    portalInjectorParams.put("wt", "portal");
    portalInjectorParams.put("maxAtt", "50");
    try {
      portalWikiDataInjector.inject(portalInjectorParams);
    } catch (WikiException e) {
      LOG.error("Cannot inject portal wikis - Cause : " + e.getMessage(), e);
    }

    // Draft Pages
    try {
      Wiki intranetWiki = wikiService.getWikiByTypeAndOwner(PortalConfig.PORTAL_TYPE, "intranet");
      Page intranetWikiHome = intranetWiki.getWikiHome();

      OrganizationService organizationService = ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(OrganizationService.class);
      UserHandler userHandler = organizationService.getUserHandler();
      for(int i=1; i<=1000; i++) {
        User user = userHandler.createUserInstance("user" + i);
        userHandler.createUser(user, false);

        DraftPage draftPage = new DraftPage();
        wikiService.createDraftForNewPage(draftPage, intranetWikiHome, Calendar.getInstance().getTime().getTime());
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail("Cannot create draft pages - Cause : " + e.getMessage());
    }
  }

  public void testWikiMigration() throws WikiException {
    migrationService.start();
  }
}
