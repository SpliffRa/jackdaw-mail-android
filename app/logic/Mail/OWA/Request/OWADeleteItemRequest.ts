import { OWARequest } from "./OWARequest";

export class OWADeleteItemRequest extends OWARequest {
  Body: any = {
    __type: "DeleteItemRequest:#Exchange",
    ItemIds: [{
      __type: "ItemId:#Exchange",
    }],
    DeleteType: "MoveToDeletedItems",
  };

  constructor(ids: string | readonly string[], attributes?: { [key: string]: string | boolean }) {
    super("DeleteItem");
    let itemIDs = typeof ids == "string" ? [ids] : ids;
    this.Body.ItemIds = itemIDs.map(id => ({
      __type: "ItemId:#Exchange",
      Id: id,
    }));
    Object.assign(this.Body, attributes);
  }
}
