/*
 *
 *  * Copyright (C) 2003-2015 eXo Platform SAS.
 *  *
 *  * This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either version 3
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see<http://www.gnu.org/licenses/>.
 *
 */

package org.exoplatform.wiki.jpa;

import org.exoplatform.commons.api.search.SearchService;
import org.exoplatform.commons.utils.ObjectPageList;
import org.exoplatform.commons.utils.PageList;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.services.security.Identity;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.jpa.dao.*;
import org.exoplatform.wiki.jpa.entity.*;
import org.exoplatform.wiki.mow.api.*;
import org.exoplatform.wiki.mow.api.Attachment;
import org.exoplatform.wiki.mow.api.DraftPage;
import org.exoplatform.wiki.mow.api.EmotionIcon;
import org.exoplatform.wiki.mow.api.Page;
import org.exoplatform.wiki.mow.api.PermissionType;
import org.exoplatform.wiki.mow.api.Template;
import org.exoplatform.wiki.mow.api.Wiki;
import org.exoplatform.wiki.service.DataStorage;
import org.exoplatform.wiki.service.WikiPageParams;
import org.exoplatform.wiki.service.search.*;
import org.exoplatform.wiki.utils.WikiConstants;

import java.util.*;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 * exo@exoplatform.com
 * 9/8/15
 */
public class JPADataStorage implements DataStorage {

  private WikiDAO wikiDAO;
  private PageDAO pageDAO;
  private AttachmentDAO attachmentDAO;
  private TemplateDAO templateDAO;
  private EmotionIconDAO emotionIconDAO;

  public JPADataStorage(WikiDAO wikiDAO, PageDAO pageDAO, AttachmentDAO attachmentDAO,
                        TemplateDAO templateDAO, EmotionIconDAO emotionIconDAO) {
    this.wikiDAO = wikiDAO;
    this.pageDAO = pageDAO;
    this.attachmentDAO = attachmentDAO;
    this.templateDAO = templateDAO;
    this.emotionIconDAO = emotionIconDAO;
  }

  @Override
  public PageList<SearchResult> search(WikiSearchData wikiSearchData) {
    List<SearchResult> searchResults = new ArrayList<>();
    Map<String, Collection<org.exoplatform.commons.api.search.data.SearchResult>> results;
    SearchService searchService = PortalContainer.getInstance().getComponentInstanceOfType(SearchService.class);

    results = searchService.search(null, wikiSearchData.getTitle(), null, Collections.singleton("all"),
            (int) wikiSearchData.getOffset(), wikiSearchData.getLimit(),
            wikiSearchData.getSort(), wikiSearchData.getOrder());
    for (String type : results.keySet()) {
      for (org.exoplatform.commons.api.search.data.SearchResult result : results.get(type)) {
        searchResults.add(toSearchResult(result));
      }
    }
    return new ObjectPageList<>(searchResults, searchResults.size());
  }

  private SearchResult toSearchResult(org.exoplatform.commons.api.search.data.SearchResult input) {
    SearchResult output = new SearchResult();
    output.setTitle(input.getTitle());
    return output;
  }

  @Override
  public Wiki getWikiByTypeAndOwner(String wikiType, String wikiOwner) throws WikiException {
    return convertWikiEntityToWiki(wikiDAO.getWikiByTypeAndOwner(wikiType, wikiOwner));
  }

  @Override
  public List<Wiki> getWikisByType(String wikiType) throws WikiException {
    List<Wiki> wikis = new ArrayList();
    for(org.exoplatform.wiki.jpa.entity.Wiki wikiEntity : wikiDAO.getWikisByType(wikiType)) {
      wikis.add(convertWikiEntityToWiki(wikiEntity));
    }
    return wikis;
  }

  @Override
  public Wiki createWiki(Wiki wiki) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Wiki createdWikiEntity = wikiDAO.create(convertWikiToWikiEntity(wiki));
    Wiki createdWiki = convertWikiEntityToWiki(createdWikiEntity);

    // create wiki home page
    Page wikiHomePage = new Page();
    wikiHomePage.setWikiType(wiki.getType());
    wikiHomePage.setWikiOwner(wiki.getOwner());
    wikiHomePage.setName(WikiConstants.WIKI_HOME_NAME);
    wikiHomePage.setTitle(WikiConstants.WIKI_HOME_TITLE);
    Date now = Calendar.getInstance().getTime();
    wikiHomePage.setCreatedDate(now);
    wikiHomePage.setUpdatedDate(now);
    wikiHomePage.setContent("= Welcome to " + wiki.getOwner() + " =");

    Page createdWikiHomePage = createPage(createdWiki, null, wikiHomePage);
    createdWiki.setWikiHome(createdWikiHomePage);

    return createdWiki;
  }

  @Override
  public Page createPage(Wiki wiki, Page parentPage, Page page) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Wiki wikiEntity = wikiDAO.getWikiByTypeAndOwner(wiki.getType(), wiki.getOwner());
    if(wikiEntity == null) {
      throw new WikiException("Cannot create page " + wiki.getType() + ":" + wiki.getOwner() + ":"
              + page.getName() + " because wiki does not exist.");
    }

    org.exoplatform.wiki.jpa.entity.Page parentPageEntity = null;
    if(parentPage != null) {
      parentPageEntity = pageDAO.getPageOfWikiByName(wiki.getType(), wiki.getOwner(), parentPage.getName());
      if(parentPageEntity == null) {
        throw new WikiException("Cannot create page " + wiki.getType() + ":" + wiki.getOwner() + ":"
                + page.getName() + " because parent page " + parentPage.getName() + " does not exist.");
      }
    }
    org.exoplatform.wiki.jpa.entity.Page pageEntity = convertPageToPageEntity(page);
    pageEntity.setWiki(wikiEntity);
    pageEntity.setParentPage(parentPageEntity);

    org.exoplatform.wiki.jpa.entity.Page createdPageEntity = pageDAO.create(pageEntity);

    // if the page to create is the wiki home, update the wiki
    if(parentPage == null) {
      wikiEntity.setWikiHome(createdPageEntity);
      wikiDAO.update(wikiEntity);
    }

    return convertPageEntityToPage(createdPageEntity);
  }

  @Override
  public Page getPageOfWikiByName(String wikiType, String wikiOwner, String pageName) throws WikiException {
    return convertPageEntityToPage(pageDAO.getPageOfWikiByName(wikiType, wikiOwner, pageName));
  }

  @Override
  public Page getPageById(String id) throws WikiException {
    return convertPageEntityToPage(pageDAO.find(Long.parseLong(id)));
  }

  @Override
  public Page getParentPageOf(Page page) throws WikiException {
    Page parentPage = null;

    org.exoplatform.wiki.jpa.entity.Page childPageEntity = null;
    if(page.getId() != null && !page.getId().isEmpty()) {
      childPageEntity = pageDAO.find(Long.parseLong(page.getId()));
    } else {
      childPageEntity = pageDAO.getPageOfWikiByName(page.getWikiType(), page.getWikiOwner(), page.getName());
    }

    if(childPageEntity != null) {
      parentPage = convertPageEntityToPage(childPageEntity.getParentPage());
    }

    return parentPage;
  }

  @Override
  public List<Page> getChildrenPageOf(Page page) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity = pageDAO.getPageOfWikiByName(page.getWikiType(), page.getWikiOwner(), page.getName());
    if(pageEntity == null) {
      throw new WikiException("Cannot get children of page " + page.getWikiType() + ":" + page.getWikiOwner() + ":"
              + page.getName() + " because page does not exist.");
    }

    List<Page> childrenPages = new ArrayList<>();
    List<org.exoplatform.wiki.jpa.entity.Page> childrenPagesEntities = pageDAO.getChildrenPages(pageEntity);
    if(childrenPagesEntities != null) {
      for (org.exoplatform.wiki.jpa.entity.Page childPageEntity : childrenPagesEntities) {
        childrenPages.add(convertPageEntityToPage(childPageEntity));
      }
    }

    return childrenPages;
  }

  @Override
  public void deletePage(String wikiType, String wikiOwner, String pageName) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity = pageDAO.getPageOfWikiByName(wikiType, wikiOwner, pageName);
    if(pageEntity == null) {
      throw new WikiException("Cannot delete page " + wikiType + ":" + wikiOwner + ":" + pageName
              + " because page does not exist.");
    }

    // delete the page and all its children pages (listeners call on delete page event is done on service layer)
    deletePageEntity(pageEntity);
  }

  /**
   * Recursively deletes a page and all its children pages
   * @param pageEntity the root page to delete
   */
  private void deletePageEntity(org.exoplatform.wiki.jpa.entity.Page pageEntity) {
    List<org.exoplatform.wiki.jpa.entity.Page> childrenPages = pageDAO.getChildrenPages(pageEntity);
    if(childrenPages != null) {
      for (org.exoplatform.wiki.jpa.entity.Page childPage : childrenPages) {
        deletePageEntity(childPage);
      }
    }

    pageDAO.delete(pageEntity);
  }

  @Override
  public void createTemplatePage(Wiki wiki, Template template) throws WikiException {
    template.setWikiId(wiki.getId());
    template.setWikiType(wiki.getType());
    template.setWikiOwner(wiki.getOwner());
    templateDAO.create(convertTemplateToTemplateEntity(template));
  }

  @Override
  public void updateTemplatePage(Template template) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Template templateEntity;
    if(template.getId() != null && !template.getId().isEmpty()) {
      templateEntity = templateDAO.find(Long.parseLong(template.getId()));
    } else {
      templateEntity = templateDAO.getTemplateOfWikiByName(template.getWikiType(), template.getWikiOwner(), template.getName());
    }

    if(templateEntity == null) {
      throw new WikiException("Cannot update template " + template.getWikiType() + ":" + template.getWikiOwner() + ":"
              + template.getName() + " because template does not exist.");
    }

    templateEntity.setName(template.getName());
    templateEntity.setTitle(template.getTitle());
    templateEntity.setContent(template.getContent());
    templateEntity.setSyntax(template.getSyntax());

    templateDAO.update(templateEntity);
  }

  @Override
  public void deleteTemplatePage(String wikiType, String wikiOwner, String templateName) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Template templateEntity = templateDAO.getTemplateOfWikiByName(wikiType, wikiOwner, templateName);
    if(templateEntity == null) {
      throw new WikiException("Cannot delete template " + wikiType + ":" + wikiOwner + ":" + templateName
              + " because template does not exist.");
    }

    templateDAO.delete(templateEntity);
  }

  @Override
  public Template getTemplatePage(WikiPageParams params, String templateName) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Template templateEntity = templateDAO.getTemplateOfWikiByName(params.getType(), params.getOwner(), templateName);
    return convertTemplateEntityToTemplate(templateEntity);
  }

  @Override
  public Map<String, Template> getTemplates(WikiPageParams wikiPageParams) throws WikiException {
    Map<String, Template> templates = new HashMap<>();

    List<org.exoplatform.wiki.jpa.entity.Template> templatesEntities = templateDAO.getTemplatesOfWiki(wikiPageParams.getType(), wikiPageParams.getOwner());
    if(templatesEntities != null) {
      for(org.exoplatform.wiki.jpa.entity.Template templateEntity : templatesEntities) {
        templates.put(templateEntity.getName(), convertTemplateEntityToTemplate(templateEntity));
      }
    }

    return templates;
  }

  @Override
  public void deleteDraftOfPage(Page page, String s) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public void deleteDraftByName(String s, String s1) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public void renamePage(String wikiType, String wikiOwner, String pageName, String newName, String newTitle) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity = pageDAO.getPageOfWikiByName(wikiType, wikiOwner, pageName);
    if(pageEntity == null) {
      throw new WikiException("Cannot rename page " + wikiType + ":" + wikiOwner + ":" + pageName
              + " because page does not exist.");
    }

    pageEntity.setName(newName);
    pageEntity.setTitle(newTitle);
    pageDAO.update(pageEntity);
  }

  @Override
  public void movePage(WikiPageParams currentLocationParams, WikiPageParams newLocationParams) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity = pageDAO.getPageOfWikiByName(currentLocationParams.getType(),
            currentLocationParams.getOwner(), currentLocationParams.getPageName());
    if(pageEntity == null) {
      throw new WikiException("Cannot move page " + currentLocationParams.getType() + ":"
              + currentLocationParams.getOwner() + ":" + currentLocationParams.getPageName()
              + " because page does not exist.");
    }

    org.exoplatform.wiki.jpa.entity.Page destinationPageEntity = pageDAO.getPageOfWikiByName(newLocationParams.getType(),
            newLocationParams.getOwner(), newLocationParams.getPageName());
    if(destinationPageEntity == null) {
      throw new WikiException("Cannot move page " + currentLocationParams.getType() + ":"
              + currentLocationParams.getOwner() + ":" + currentLocationParams.getPageName() + " to page "
              + newLocationParams.getType() + ":" + newLocationParams.getOwner()
              + ":" + newLocationParams.getPageName() + " because destination page does not exist.");
    }

    pageEntity.setParentPage(destinationPageEntity);
    pageDAO.update(pageEntity);
  }

  @Override
  public List<PermissionEntry> getWikiPermission(String s, String s1) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public void updateWikiPermission(String s, String s1, List<PermissionEntry> list) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public List<Page> getRelatedPagesOfPage(Page page) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public Page getRelatedPage(String s, String s1, String s2) throws WikiException {
    // TODO Implement it !
    return null;
  }

  @Override
  public void addRelatedPage(Page page, Page page1) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public void removeRelatedPage(Page page, Page page1) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public Page getExsitedOrNewDraftPageById(String s, String s1, String s2, String s3) throws WikiException {
    // TODO Implement it !
    return null;
  }

  @Override
  public DraftPage getDraft(WikiPageParams wikiPageParams, String s) throws WikiException {
    // TODO Implement it !
    return null;
  }

  @Override
  public DraftPage getLastestDraft(String s) throws WikiException {
    // TODO Implement it !
    return null;
  }

  @Override
  public DraftPage getDraft(String s, String s1) throws WikiException {
    // TODO Implement it !
    return null;
  }

  @Override
  public List<DraftPage> getDraftPagesOfUser(String s) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public void createDraftPageForUser(DraftPage draftPage, String s) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public List<TemplateSearchResult> searchTemplate(TemplateSearchData templateSearchData) throws WikiException {
    List<org.exoplatform.wiki.jpa.entity.Template> templates = templateDAO.searchTemplatesByTitle(templateSearchData.getWikiType(),
            templateSearchData.getWikiOwner(), templateSearchData.getTitle());

    List<TemplateSearchResult> searchResults = new ArrayList<>();
    if(templates != null) {
      for (org.exoplatform.wiki.jpa.entity.Template templateEntity : templates) {
        TemplateSearchResult templateSearchResult = new TemplateSearchResult(templateEntity.getWiki().getType(),
                templateEntity.getWiki().getOwner(),
                templateEntity.getName(),
                templateEntity.getTitle(),
                null,
                SearchResultType.TEMPLATE,
                null,
                null,
                null);
        searchResults.add(templateSearchResult);
      }
    }

    return searchResults;
  }

  @Override
  public List<SearchResult> searchRenamedPage(WikiSearchData wikiSearchData) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public List<Attachment> getAttachmentsOfPage(Page page) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity;
    if(page.getId() != null && !page.getId().isEmpty()) {
      pageEntity = pageDAO.find(Long.parseLong(page.getId()));
    } else {
      pageEntity = pageDAO.getPageOfWikiByName(page.getWikiType(), page.getWikiOwner(), page.getName());
    }

    if(pageEntity == null) {
      throw new WikiException("Cannot get attachments of page " + page.getWikiType() + ":" + page.getWikiOwner() + ":"
              + page.getName() + " because page does not exist.");
    }

    List<Attachment> attachments = new ArrayList<>();
    List<org.exoplatform.wiki.jpa.entity.Attachment> attachmentsEntities = pageEntity.getAttachments();
    if(attachmentsEntities != null) {
      for(org.exoplatform.wiki.jpa.entity.Attachment attachmentEntity : attachmentsEntities) {
        attachments.add(convertAttachmentEntityToAttachment(attachmentEntity));
      }
    }

    return attachments;
  }

  @Override
  public void addAttachmentToPage(Attachment attachment, Page page) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity;
    if(page.getId() != null && !page.getId().isEmpty()) {
      pageEntity = pageDAO.find(Long.parseLong(page.getId()));
    } else {
      pageEntity = pageDAO.getPageOfWikiByName(page.getWikiType(), page.getWikiOwner(), page.getName());
    }

    if(pageEntity == null) {
      throw new WikiException("Cannot add an attachment to page " + page.getWikiType() + ":" + page.getWikiOwner() + ":"
              + page.getName() + " because page does not exist.");
    }

    org.exoplatform.wiki.jpa.entity.Attachment attachmentEntity = attachmentDAO.create(convertAttachmentToAttachmentEntity(attachment));

    List<org.exoplatform.wiki.jpa.entity.Attachment> attachmentsEntities = pageEntity.getAttachments();
    if(attachmentsEntities == null) {
      attachmentsEntities = new ArrayList<>();
    }
    attachmentsEntities.add(attachmentEntity);
    pageEntity.setAttachments(attachmentsEntities);
    pageDAO.update(pageEntity);
  }

  @Override
  public void deleteAttachmentOfPage(String attachmentName, Page page) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity;
    if(page.getId() != null && !page.getId().isEmpty()) {
      pageEntity = pageDAO.find(Long.parseLong(page.getId()));
    } else {
      pageEntity = pageDAO.getPageOfWikiByName(page.getWikiType(), page.getWikiOwner(), page.getName());
    }

    if(pageEntity == null) {
      throw new WikiException("Cannot delete an attachment of page " + page.getWikiType() + ":" + page.getWikiOwner() + ":"
              + page.getName() + " because page does not exist.");
    }

    boolean attachmentFound = false;
    List<org.exoplatform.wiki.jpa.entity.Attachment> attachmentsEntities = pageEntity.getAttachments();
    if(attachmentsEntities != null) {
      for (int i = 0; i < attachmentsEntities.size(); i++) {
        org.exoplatform.wiki.jpa.entity.Attachment attachmentEntity = attachmentsEntities.get(i);
        if (attachmentEntity.getName() != null && attachmentEntity.getName().equals(attachmentName)) {
          attachmentFound = true;
          attachmentsEntities.remove(i);
          attachmentDAO.delete(attachmentEntity);
          pageEntity.setAttachments(attachmentsEntities);
          pageDAO.update(pageEntity);
          break;
        }
      }
    }

    if(!attachmentFound) {
      throw new WikiException("Cannot delete the attachment " + attachmentName + " of page " + page.getWikiType() + ":" + page.getWikiOwner() + ":"
              + page.getName() + " because attachment does not exist.");
    }
  }

  @Override
  public Page getHelpSyntaxPage(String s, List<ValuesParam> list, ConfigurationManager configurationManager) throws WikiException {
    // TODO Implement it !
    return null;
  }

  @Override
  public void createEmotionIcon(EmotionIcon emotionIcon) throws WikiException {
    org.exoplatform.wiki.jpa.entity.EmotionIcon emotionIconEntity = new org.exoplatform.wiki.jpa.entity.EmotionIcon();
    emotionIconEntity.setName(emotionIcon.getName());
    emotionIconEntity.setImage(emotionIcon.getImage());

    emotionIconDAO.create(emotionIconEntity);
  }

  @Override
  public List<EmotionIcon> getEmotionIcons() throws WikiException {
    List<EmotionIcon> emotionIcons = new ArrayList<>();
    List<org.exoplatform.wiki.jpa.entity.EmotionIcon> emotionIconsEntities = emotionIconDAO.findAll();
    if(emotionIconsEntities != null) {
      for (org.exoplatform.wiki.jpa.entity.EmotionIcon emotionIconEntity : emotionIconsEntities) {
        emotionIcons.add(convertEmotionIconEntityToEmotionIcon(emotionIconEntity));
      }
    }
    return emotionIcons;
  }

  @Override
  public EmotionIcon getEmotionIconByName(String emotionIconName) throws WikiException {
    return convertEmotionIconEntityToEmotionIcon(emotionIconDAO.getEmotionIconByName(emotionIconName));
  }

  @Override
  public String getPortalOwner() throws WikiException {
    // TODO Implement it !
    return "";
  }

  @Override
  public boolean hasPermissionOnPage(Page page, PermissionType permissionType, Identity identity) throws WikiException {
    // TODO Implement it !
    return true;
  }

  @Override
  public boolean hasAdminSpacePermission(String s, String s1, Identity identity) throws WikiException {
    // TODO Implement it !
    return true;
  }

  @Override
  public boolean hasAdminPagePermission(String s, String s1, Identity identity) throws WikiException {
    // TODO Implement it !
    return true;
  }

  @Override
  public List<PageVersion> getVersionsOfPage(Page page) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public void addPageVersion(Page page) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public void restoreVersionOfPage(String s, Page page) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public void updatePage(Page page) throws WikiException {
    org.exoplatform.wiki.jpa.entity.Page pageEntity;
    if(page.getId() != null && !page.getId().isEmpty()) {
      pageEntity = pageDAO.find(Long.parseLong(page.getId()));
    } else {
      pageEntity = pageDAO.getPageOfWikiByName(page.getWikiType(), page.getWikiOwner(), page.getName());
    }

    if(pageEntity == null) {
      throw new WikiException("Cannot update page " + page.getWikiType() + ":" + page.getWikiOwner() + ":"
              + page.getName() + " because page does not exist.");
    }

    pageEntity.setName(page.getName());
    pageEntity.setTitle(page.getTitle());
    pageEntity.setAuthor(page.getAuthor());
    pageEntity.setContent(page.getContent());
    pageEntity.setSyntax(page.getSyntax());
    pageEntity.setCreatedDate(page.getCreatedDate());
    pageEntity.setUpdatedDate(page.getUpdatedDate());
    pageEntity.setMinorEdit(page.isMinorEdit());
    pageEntity.setComment(page.getComment());
    pageEntity.setUrl(page.getUrl());
    //page.setPermissions(pageEntity.getPermissions());
    pageEntity.setActivityId(page.getActivityId());

    pageDAO.update(pageEntity);
  }

  @Override
  public List<String> getPreviousNamesOfPage(Page page) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public List<String> getWatchersOfPage(Page page) throws WikiException {
    // TODO Implement it !
    return new ArrayList<>();
  }

  @Override
  public void addWatcherToPage(String s, Page page) throws WikiException {
    // TODO Implement it !
  }

  @Override
  public void deleteWatcherOfPage(String s, Page page) throws WikiException {
    // TODO Implement it !
  }

  private Wiki convertWikiEntityToWiki(org.exoplatform.wiki.jpa.entity.Wiki wikiEntity) {
    Wiki wiki = null;
    if(wikiEntity != null) {
      wiki = new Wiki();
      wiki.setId(String.valueOf(wikiEntity.getId()));
      wiki.setType(wikiEntity.getType());
      wiki.setOwner(wikiEntity.getOwner());
      org.exoplatform.wiki.jpa.entity.Page wikiHomePageEntity = wikiEntity.getWikiHome();
      if(wikiHomePageEntity != null) {
        wiki.setWikiHome(convertPageEntityToPage(wikiHomePageEntity));
      }
      //wiki.setPermissions(wikiEntity.getPermissions());
      //wiki.setDefaultPermissionsInited();
      wiki.setPreferences(wiki.getPreferences());
    }
    return wiki;
  }

  private org.exoplatform.wiki.jpa.entity.Wiki convertWikiToWikiEntity(Wiki wiki) {
    org.exoplatform.wiki.jpa.entity.Wiki wikiEntity = null;
    if(wiki != null) {
      wikiEntity = new org.exoplatform.wiki.jpa.entity.Wiki();
      wikiEntity.setType(wiki.getType());
      wikiEntity.setOwner(wiki.getOwner());
      wikiEntity.setWikiHome(convertPageToPageEntity(wiki.getWikiHome()));
      //wikiEntity.setPermissions(wiki.getPermissions());
    }
    return wikiEntity;
  }

  private Page convertPageEntityToPage(org.exoplatform.wiki.jpa.entity.Page pageEntity) {
    Page page = null;
    if(pageEntity != null) {
      page = new Page();
      page.setId(String.valueOf(pageEntity.getId()));
      page.setName(pageEntity.getName());
      org.exoplatform.wiki.jpa.entity.Wiki wiki = pageEntity.getWiki();
      if(wiki != null) {
        page.setWikiId(String.valueOf(wiki.getId()));
        page.setWikiType(wiki.getType());
        page.setWikiOwner(wiki.getOwner());
      }
      page.setTitle(pageEntity.getTitle());
      page.setAuthor(pageEntity.getAuthor());
      page.setContent(pageEntity.getContent());
      page.setSyntax(pageEntity.getSyntax());
      page.setCreatedDate(pageEntity.getCreatedDate());
      page.setUpdatedDate(pageEntity.getUpdatedDate());
      page.setMinorEdit(pageEntity.isMinorEdit());
      page.setComment(pageEntity.getComment());
      page.setUrl(pageEntity.getUrl());
      //page.setPermissions(pageEntity.getPermissions());
      page.setActivityId(pageEntity.getActivityId());
    }
    return page;
  }

  private org.exoplatform.wiki.jpa.entity.Page convertPageToPageEntity(Page page) {
    org.exoplatform.wiki.jpa.entity.Page pageEntity = null;
    if(page != null) {
      pageEntity = new org.exoplatform.wiki.jpa.entity.Page();
      pageEntity.setName(page.getName());
      if(page.getWikiId() != null) {
        org.exoplatform.wiki.jpa.entity.Wiki wiki = wikiDAO.find(Long.parseLong(page.getWikiId()));
        if (wiki != null) {
          pageEntity.setWiki(wiki);
        }
      }
      pageEntity.setTitle(page.getTitle());
      pageEntity.setAuthor(page.getAuthor());
      pageEntity.setContent(page.getContent());
      pageEntity.setSyntax(page.getSyntax());
      pageEntity.setCreatedDate(page.getCreatedDate());
      pageEntity.setUpdatedDate(page.getUpdatedDate());
      pageEntity.setMinorEdit(page.isMinorEdit());
      pageEntity.setComment(page.getComment());
      pageEntity.setUrl(page.getUrl());
      //page.setPermissions(pageEntity.getPermissions());
      pageEntity.setActivityId(page.getActivityId());
    }
    return pageEntity;
  }

  private Attachment convertAttachmentEntityToAttachment(org.exoplatform.wiki.jpa.entity.Attachment attachmentEntity) {
    Attachment attachment = null;
    if(attachmentEntity != null) {
      attachment = new Attachment();
      attachment.setName(attachmentEntity.getName());
      attachment.setTitle(attachmentEntity.getTitle());
      attachment.setCreator(attachmentEntity.getCreator());
      if(attachmentEntity.getCreatedDate() != null) {
        Calendar createdDate = Calendar.getInstance();
        createdDate.setTime(attachmentEntity.getCreatedDate());
        attachment.setCreatedDate(createdDate);
      }
      if(attachmentEntity.getUpdatedDate() != null) {
        Calendar updatedDate = Calendar.getInstance();
        updatedDate.setTime(attachmentEntity.getUpdatedDate());
        attachment.setUpdatedDate(updatedDate);
      }
      attachment.setContent(attachmentEntity.getContent());
      //attachment.setMimeType(?);
      attachment.setDownloadURL(attachmentEntity.getDownloadURL());
      attachment.setWeightInBytes(attachmentEntity.getWeightInBytes());
      //attachment.setPermissions(?);
    }
    return attachment;
  }

  private org.exoplatform.wiki.jpa.entity.Attachment convertAttachmentToAttachmentEntity(Attachment attachment) {
    org.exoplatform.wiki.jpa.entity.Attachment attachmentEntity = null;
    if(attachment != null) {
      attachmentEntity = new org.exoplatform.wiki.jpa.entity.Attachment();
      attachmentEntity.setName(attachment.getName());
      attachmentEntity.setTitle(attachment.getTitle());
      attachmentEntity.setCreator(attachment.getCreator());
      attachmentEntity.setContent(attachment.getContent());
      if(attachment.getCreatedDate() != null) {
        attachmentEntity.setCreatedDate(attachment.getCreatedDate().getTime());
      }
      if(attachment.getUpdatedDate() != null) {
        attachmentEntity.setUpdatedDate(attachment.getUpdatedDate().getTime());
      }
      attachmentEntity.setContent(attachment.getContent());
      attachmentEntity.setDownloadURL(attachment.getDownloadURL());
      //page.setPermissions(pageEntity.getPermissions());
    }
    return attachmentEntity;
  }

  private Template convertTemplateEntityToTemplate(org.exoplatform.wiki.jpa.entity.Template templateEntity) {
    Template template = null;
    if(templateEntity != null) {
      template = new Template();
      template.setId(String.valueOf(templateEntity.getId()));
      template.setName(templateEntity.getName());
      org.exoplatform.wiki.jpa.entity.Wiki wiki = templateEntity.getWiki();
      if(wiki != null) {
        template.setWikiId(String.valueOf(wiki.getId()));
        template.setWikiType(wiki.getType());
        template.setWikiOwner(wiki.getOwner());
      }
      template.setTitle(templateEntity.getTitle());
      template.setContent(templateEntity.getContent());
      template.setSyntax(templateEntity.getSyntax());
    }
    return template;
  }

  private org.exoplatform.wiki.jpa.entity.Template convertTemplateToTemplateEntity(Template template) {
    org.exoplatform.wiki.jpa.entity.Template templateEntry = null;
    if(template != null) {
      templateEntry = new org.exoplatform.wiki.jpa.entity.Template();
      templateEntry.setName(template.getName());
      if(template.getWikiId() != null) {
        org.exoplatform.wiki.jpa.entity.Wiki wiki = wikiDAO.find(Long.parseLong(template.getWikiId()));
        if (wiki != null) {
          templateEntry.setWiki(wiki);
        }
      }
      templateEntry.setTitle(template.getTitle());
      templateEntry.setContent(template.getContent());
      templateEntry.setSyntax(template.getSyntax());
    }
    return templateEntry;
  }

  private EmotionIcon convertEmotionIconEntityToEmotionIcon(org.exoplatform.wiki.jpa.entity.EmotionIcon emotionIconEntity) {
    EmotionIcon emotionIcon = null;
    if(emotionIconEntity != null) {
      emotionIcon = new EmotionIcon();
      emotionIcon.setName(emotionIconEntity.getName());
      emotionIcon.setImage(emotionIconEntity.getImage());
    }
    return emotionIcon;
  }

  private org.exoplatform.wiki.jpa.entity.EmotionIcon convertEmotionIconToEmotionIconEntity(EmotionIcon emotionIcon) {
    org.exoplatform.wiki.jpa.entity.EmotionIcon emotionIconEntity = null;
    if(emotionIcon != null) {
      emotionIconEntity = new org.exoplatform.wiki.jpa.entity.EmotionIcon();
      emotionIconEntity.setName(emotionIcon.getName());
      emotionIconEntity.setImage(emotionIcon.getImage());
    }
    return emotionIconEntity;
  }

}
