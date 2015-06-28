package sage.domain.service;

import java.util.Collection;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sage.domain.commons.AuthorityException;
import sage.domain.commons.DomainRuntimeException;
import sage.domain.commons.IdCommons;
import sage.domain.concept.Authority;
import sage.domain.repository.TagChangeRequestRepository;
import sage.domain.repository.TagRepository;
import sage.domain.repository.UserRepository;
import sage.entity.Tag;
import sage.entity.TagChangeRequest;
import sage.entity.TagChangeRequest.Status;
import sage.entity.TagChangeRequest.Type;
import sage.entity.User;
import sage.transfer.TagChangeRequestCard;
import sage.util.Colls;

@Service
@Transactional
public class TagChangeService {
  @Autowired
  private TagRepository tagRepo;
  @Autowired
  private TagChangeRequestRepository reqRepo;
  @Autowired
  private UserRepository userRepo;

  public Long newTag(String name, long parentId, String intro) {
    if (intro == null || intro.isEmpty()) {
      intro = "啊，" + name + "！";
    }
    Tag tag = new Tag(name, tagRepo.load(parentId), intro);
    if (tagRepo.byNameAndParent(name, parentId) == null) {
      tagRepo.save(tag);
      return tag.getId();
    } else {
      throw new DomainRuntimeException("Tag[name: %s, parentId: %s] already exists", name, parentId);
    }
  }

  public TagChangeRequest requestMove(Long userId, Long tagId, Long parentId) {
    return saveRequest(TagChangeRequest.forMove(tagRepo.load(tagId), userRepo.load(userId), parentId));
  }

  public TagChangeRequest requestRename(Long userId, Long tagId, String name) {
    return saveRequest(TagChangeRequest.forRename(tagRepo.load(tagId), userRepo.load(userId), name));
  }

  public TagChangeRequest requestSetIntro(Long userId, Long tagId, String intro) {
    return saveRequest(TagChangeRequest.forSetIntro(tagRepo.load(tagId), userRepo.load(userId), intro));
  }

  private TagChangeRequest saveRequest(TagChangeRequest req) {
    reqRepo.save(req);
    User submitter = req.getSubmitter();
    if (Authority.isTagAdminOrHigher(submitter.getAuthority())) {
      acceptRequest(submitter.getId(), req.getId());
      reqRepo.update(req);
    }
    return req;
  }

  public Collection<TagChangeRequestCard> getRequestsOfTag(long tagId) {
    return Colls.map(reqRepo.byTag(tagId), TagChangeRequestCard::new);
  }

  public Collection<TagChangeRequestCard> getRequestsOfTagScope(long tagId) {
    return Colls.map(reqRepo.byTagScope(tagRepo.get(tagId)), TagChangeRequestCard::new);
  }

  public void cancelRequest(Long userId, Long reqId) {
    TagChangeRequest request = reqRepo.get(reqId);
    if (!IdCommons.equal(request.getSubmitter().getId(), userId)) {
      throw new DomainRuntimeException("User[%d] is not the owner of TagChangeRequest[%d]", userId, reqId);
    }
    request.setStatus(Status.CANCELED);
  }

  public void acceptRequest(Long userId, Long reqId) {
    transactRequest(userId, reqId, Status.ACCEPTED);
  }

  public void rejectRequest(Long userId, Long reqId) {
    transactRequest(userId, reqId, Status.REJECTED);
  }

  private void transactRequest(Long userId, Long reqId, Status status) {
    User user = userRepo.get(userId);
    if (!Authority.isTagAdminOrHigher(user.getAuthority())) {
      throw new AuthorityException("Require TagAdmin or higher.");
    }
    TagChangeRequest req = reqRepo.get(reqId);
    req.setStatus(status);
    req.setTransactor(user);

    if (status == Status.ACCEPTED) {
      Long tagId = req.getTag().getId();
      if (req.getType() == Type.MOVE) {
        doTransact(tagId, tag -> tag.setParent(tagRepo.load(req.getParentId())));
      } else if (req.getType() == Type.RENAME) {
        doTransact(tagId, tag -> tag.setName(req.getName()));
      } else if (req.getType() == Type.SET_INTRO) {
        doTransact(tagId, tag -> tag.setIntro(req.getIntro()));
      }
    }
  }

  private void doTransact(long tagId, Consumer<Tag> action) {
    Tag tag = tagRepo.get(tagId);
    action.accept(tag);
    tagRepo.update(tag);
  }

}
