/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.wiki.jpa.dao;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.wiki.jpa.entity.AttachmentEntity;
import org.exoplatform.wiki.jpa.entity.PageAttachmentEntity;

import java.util.List;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Jun 24, 2015  
 */
public class PageAttachmentDAO extends WikiBaseDAO<PageAttachmentEntity,Long> {

  public List<Long> findAllIds(int offset, int limit) {
    return getEntityManager().createNamedQuery("pageAttachment.getAllIds").setFirstResult(offset).setMaxResults(limit).getResultList();
  }

  public List<Long> getAttachmentIdByPageId(long pageId) {
    return getEntityManager().createNamedQuery("pageAttachment.getAllIdsByPageId").setParameter("pageId", pageId).getResultList();
  }
}
